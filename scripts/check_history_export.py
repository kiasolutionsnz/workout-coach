"""Validate the real shared export with an independent JSON parser."""
from pathlib import Path
import json
root=Path(__file__).resolve().parents[1]
data=json.loads((root/'shared/build/test-fixtures/workout.json').read_text(encoding='utf-8'))
assert set(data)=={'schemaVersion','workout'} and data['schemaVersion']==1
workout=data['workout']
assert set(workout)=={'id','routine','startedEpochMillis','status','sets'}
assert workout['routine']=='Routine "quoted"\nMāori\t\\path'
assert workout['status']=='Completed'
record=workout['sets'][0]
assert set(record)=={'ordinal','exercise','target','holdTiming','accepted','partial','activeMillis','validHoldMillis','reachedGoal','reps'}
assert record['target']==29 and record['holdTiming']=='VALID_HOLD'
assert record['activeMillis']==30000 and record['validHoldMillis']==29000
assert record['reachedGoal'] is True
print('History export passed independent JSON parse, escaping, exact schema and timed-hold semantics.')
