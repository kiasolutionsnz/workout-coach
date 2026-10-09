-- Apply after the dedicated Auth instance has migrated auth.users.
BEGIN;
CREATE SCHEMA IF NOT EXISTS workout;
REVOKE ALL ON SCHEMA workout FROM PUBLIC;
GRANT USAGE ON SCHEMA workout TO authenticated;
DO $$ BEGIN IF NOT EXISTS(SELECT 1 FROM pg_roles WHERE rolname='workout_claims_reader') THEN CREATE ROLE workout_claims_reader NOLOGIN NOINHERIT BYPASSRLS; END IF; END $$;
ALTER ROLE workout_claims_reader BYPASSRLS;
GRANT USAGE ON SCHEMA auth TO workout_claims_reader;
GRANT SELECT(id,user_id,not_after) ON auth.sessions TO workout_claims_reader;
CREATE OR REPLACE FUNCTION auth.uid() RETURNS uuid LANGUAGE sql STABLE SECURITY DEFINER SET search_path=pg_catalog AS $$
  SELECT s.user_id FROM auth.sessions s
  WHERE s.id=NULLIF(current_setting('request.jwt.claims',true)::jsonb->>'session_id','')::uuid
    AND s.user_id=NULLIF(current_setting('request.jwt.claims',true)::jsonb->>'sub','')::uuid
    AND (s.not_after IS NULL OR s.not_after>statement_timestamp());
$$;
ALTER FUNCTION auth.uid() OWNER TO workout_claims_reader;
REVOKE ALL ON FUNCTION auth.uid() FROM PUBLIC;
GRANT USAGE ON SCHEMA auth TO authenticated;
GRANT EXECUTE ON FUNCTION auth.uid() TO authenticated;
CREATE TABLE workout.profiles (
  owner_id uuid PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
  display_name text CHECK(char_length(display_name)<=120),
  revision bigint NOT NULL DEFAULT 1 CHECK(revision>0),
  updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE workout.preferences (
  owner_id uuid PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
  voice boolean NOT NULL DEFAULT true,
  front_camera boolean NOT NULL DEFAULT true,
  revision bigint NOT NULL DEFAULT 1 CHECK(revision>0),
  updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE workout.plans (
  owner_id uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  id uuid NOT NULL,
  name text NOT NULL CHECK(char_length(trim(name)) BETWEEN 1 AND 120),
  revision bigint NOT NULL DEFAULT 1 CHECK(revision>0),
  deleted boolean NOT NULL DEFAULT false,
  updated_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY(owner_id,id)
);
CREATE TABLE workout.plan_blocks (
  owner_id uuid NOT NULL,
  plan_id uuid NOT NULL,
  ordinal integer NOT NULL CHECK(ordinal BETWEEN 0 AND 49),
  exercise text NOT NULL CHECK(exercise IN ('SQUAT','PUSH_UP','CURL','LUNGE','SHOULDER_PRESS','PLANK')),
  sets integer NOT NULL CHECK(sets BETWEEN 1 AND 20),
  goal_kind text NOT NULL CHECK(goal_kind IN ('reps','duration')),
  target integer NOT NULL CHECK(target BETWEEN 1 AND 3600),
  hold_timing text NOT NULL CHECK(hold_timing IN ('ELAPSED','VALID_HOLD')),
  rest_seconds integer NOT NULL CHECK(rest_seconds BETWEEN 0 AND 3600),
  limb_mode text NOT NULL CHECK(limb_mode IN ('BILATERAL','ALTERNATING','LEFT','RIGHT')),
  CHECK((exercise='PLANK' AND goal_kind='duration') OR (exercise<>'PLANK' AND goal_kind='reps' AND target<=100)),
  CHECK(limb_mode='BILATERAL' OR exercise IN ('CURL','LUNGE')),
  PRIMARY KEY(owner_id,plan_id,ordinal),
  FOREIGN KEY(owner_id,plan_id) REFERENCES workout.plans(owner_id,id) ON DELETE CASCADE
);
CREATE TABLE workout.sessions (
  owner_id uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  id uuid NOT NULL,
  routine_name text NOT NULL CHECK(char_length(trim(routine_name)) BETWEEN 1 AND 120),
  started_epoch_ms bigint NOT NULL CHECK(started_epoch_ms>=0),
  phase text NOT NULL CHECK(phase IN ('COMPLETED','CANCELLED','INTERRUPTED')),
  revision bigint NOT NULL DEFAULT 1 CHECK(revision>0),
  deleted boolean NOT NULL DEFAULT false,
  updated_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY(owner_id,id)
);
CREATE TABLE workout.sets (
  owner_id uuid NOT NULL,
  session_id uuid NOT NULL,
  ordinal integer NOT NULL CHECK(ordinal BETWEEN 0 AND 999),
  exercise text NOT NULL CHECK(exercise IN ('SQUAT','PUSH_UP','CURL','LUNGE','SHOULDER_PRESS','PLANK')),
  accepted integer NOT NULL CHECK(accepted BETWEEN 0 AND 100),
  partial integer NOT NULL CHECK(partial BETWEEN 0 AND 10000),
  active_ms bigint NOT NULL CHECK(active_ms BETWEEN 0 AND 86400000),
  valid_hold_ms bigint NOT NULL CHECK(valid_hold_ms BETWEEN 0 AND active_ms),
  reached_goal boolean NOT NULL,
  target integer CHECK(target BETWEEN 1 AND 3600),
  hold_timing text CHECK(hold_timing IN ('ELAPSED','VALID_HOLD')),
  PRIMARY KEY(owner_id,session_id,ordinal),
  FOREIGN KEY(owner_id,session_id) REFERENCES workout.sessions(owner_id,id) ON DELETE CASCADE
);
CREATE TABLE workout.reps (
  owner_id uuid NOT NULL,
  session_id uuid NOT NULL,
  set_ordinal integer NOT NULL,
  ordinal integer NOT NULL CHECK(ordinal BETWEEN 0 AND 10000),
  side text NOT NULL CHECK(side IN ('LEFT','RIGHT','BOTH')),
  duration_ms bigint NOT NULL CHECK(duration_ms BETWEEN 0 AND 3600000),
  is_full boolean NOT NULL,
  PRIMARY KEY(owner_id,session_id,set_ordinal,ordinal),
  FOREIGN KEY(owner_id,session_id,set_ordinal) REFERENCES workout.sets(owner_id,session_id,ordinal) ON DELETE CASCADE
);
CREATE TABLE workout.deletions (
  owner_id uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  record_id uuid NOT NULL,
  kind text NOT NULL CHECK(kind IN ('plan','workout')),
  revision bigint NOT NULL CHECK(revision>0),
  updated_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY(owner_id,record_id,kind)
);
CREATE TABLE workout.catalogue (
  exercise text PRIMARY KEY CHECK(exercise IN ('SQUAT','PUSH_UP','CURL','LUNGE','SHOULDER_PRESS','PLANK')),
  revision bigint NOT NULL CHECK(revision>0)
);
INSERT INTO workout.catalogue SELECT unnest(ARRAY['SQUAT','PUSH_UP','CURL','LUNGE','SHOULDER_PRESS','PLANK']),1;
ALTER TABLE workout.catalogue ENABLE ROW LEVEL SECURITY;
ALTER TABLE workout.catalogue FORCE ROW LEVEL SECURITY;
CREATE POLICY catalogue_read ON workout.catalogue FOR SELECT TO authenticated USING(true);
GRANT SELECT ON workout.catalogue TO authenticated;
DO $$ DECLARE tbl text; BEGIN
  FOREACH tbl IN ARRAY ARRAY['profiles','preferences','plans','plan_blocks','sessions','sets','reps','deletions'] LOOP
    EXECUTE format('ALTER TABLE workout.%I ENABLE ROW LEVEL SECURITY',tbl);
    EXECUTE format('ALTER TABLE workout.%I FORCE ROW LEVEL SECURITY',tbl);
    EXECUTE format('CREATE POLICY owner_access ON workout.%I TO authenticated USING ((SELECT auth.uid())=owner_id) WITH CHECK ((SELECT auth.uid())=owner_id)',tbl);
    EXECUTE format('GRANT SELECT,INSERT,UPDATE,DELETE ON workout.%I TO authenticated',tbl);
  END LOOP;
END $$;
CREATE FUNCTION workout.advance_revision() RETURNS trigger LANGUAGE plpgsql SET search_path=pg_catalog AS $$
BEGIN
  IF NEW.owner_id<>OLD.owner_id THEN RAISE EXCEPTION 'Owner cannot change'; END IF;
  IF NEW.revision<>OLD.revision+1 THEN RAISE EXCEPTION 'Revision conflict'; END IF;
  NEW.updated_at=statement_timestamp();RETURN NEW;
END $$;
REVOKE ALL ON FUNCTION workout.advance_revision() FROM PUBLIC;
CREATE TRIGGER plan_revision BEFORE UPDATE ON workout.plans FOR EACH ROW EXECUTE FUNCTION workout.advance_revision();
CREATE TRIGGER preferences_revision BEFORE UPDATE ON workout.preferences FOR EACH ROW EXECUTE FUNCTION workout.advance_revision();
CREATE TRIGGER profile_revision BEFORE UPDATE ON workout.profiles FOR EACH ROW EXECUTE FUNCTION workout.advance_revision();
CREATE TRIGGER session_revision BEFORE UPDATE ON workout.sessions FOR EACH ROW EXECUTE FUNCTION workout.advance_revision();
CREATE TRIGGER deletion_revision BEFORE UPDATE ON workout.deletions FOR EACH ROW EXECUTE FUNCTION workout.advance_revision();
CREATE FUNCTION workout.stamp_record() RETURNS trigger LANGUAGE plpgsql SET search_path=pg_catalog AS $$
BEGIN NEW.updated_at=statement_timestamp();RETURN NEW;END $$;
REVOKE ALL ON FUNCTION workout.stamp_record() FROM PUBLIC;
DO $$ DECLARE tbl text; BEGIN
  FOREACH tbl IN ARRAY ARRAY['profiles','preferences','plans','sessions','deletions'] LOOP
    EXECUTE format('CREATE TRIGGER server_timestamp BEFORE INSERT ON workout.%I FOR EACH ROW EXECUTE FUNCTION workout.stamp_record()',tbl);
  END LOOP;
END $$;
CREATE INDEX session_owner_started ON workout.sessions(owner_id,started_epoch_ms DESC,id);
CREATE INDEX plan_owner_updated ON workout.plans(owner_id,updated_at,id);
CREATE INDEX session_owner_updated ON workout.sessions(owner_id,updated_at,id);
REVOKE ALL ON ALL TABLES IN SCHEMA workout FROM anon;
REVOKE ALL ON ALL FUNCTIONS IN SCHEMA workout FROM anon;
NOTIFY pgrst, 'reload schema';
COMMIT;
