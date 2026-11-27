# Reads a Perfetto trace of 5broz and prints where frame time goes: frame durations on the main
# thread and RenderThread, then the composables / view work that took the most time.
import sys
from perfetto.trace_processor import TraceProcessor

tp = TraceProcessor(trace=sys.argv[1])
pkg = "com.fivebroz.android"

def q(sql):
    return list(tp.query(sql))

ut = q(f"""select utid, thread.name as tname from thread join process using(upid)
           where process.name = '{pkg}' and (thread.is_main_thread = 1 or thread.name = 'RenderThread')""")
print("threads:", [(r.utid, r.tname) for r in ut])

for name, label in [("Choreographer#doFrame%", "main thread frame"), ("DrawFrame%", "RenderThread frame")]:
    rows = q(f"""select dur from slice join thread_track on slice.track_id = thread_track.id
                 join thread using(utid) join process using(upid)
                 where process.name = '{pkg}' and slice.name like '{name}' order by dur""")
    d = [r.dur / 1e6 for r in rows]
    if d:
        pct = lambda p: d[min(len(d) - 1, int(len(d) * p))]
        print(f"{label}: n={len(d)} p50={pct(0.5):.1f}ms p90={pct(0.9):.1f}ms max={d[-1]:.1f}ms over16ms={sum(x > 16.7 for x in d)}")

print("\\nTop work on the main thread (total ms, count, name):")
for r in q(f"""select slice.name as name, sum(dur)/1e6 as total, count(*) as n, max(dur)/1e6 as mx
               from slice join thread_track on slice.track_id = thread_track.id
               join thread using(utid) join process using(upid)
               where process.name = '{pkg}' and thread.is_main_thread = 1 and slice.depth > 0
               group by slice.name order by total desc limit 45"""):
    print(f"{r.total:9.1f}  {r.n:6d}  max {r.mx:6.1f}  {r.name[:110]}")

print("\\nTop work on RenderThread:")
for r in q(f"""select slice.name as name, sum(dur)/1e6 as total, count(*) as n
               from slice join thread_track on slice.track_id = thread_track.id
               join thread using(utid) join process using(upid)
               where process.name = '{pkg}' and thread.name = 'RenderThread'
               group by slice.name order by total desc limit 20"""):
    print(f"{r.total:9.1f}  {r.n:6d}  {r.name[:110]}")
