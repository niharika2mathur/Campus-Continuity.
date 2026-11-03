#!/usr/bin/env python3
"""Reproducible generator for the bundled demo dataset (DATA ONLY - no assessed algorithm lives in Python).
Usage: python3 generate_sample_data.py [seed]   (default seed is the one used for the committed CSVs)"""
import csv, random, sys
seed = int(sys.argv[1]) if len(sys.argv) > 1 else 11
r = random.Random(seed)
labs = [("LAB-AI1","AI Research Lab","Block A",1,6,"gpu;linux;matlab"),("LAB-AI2","Deep Learning Lab","Block A",1,6,"gpu;linux"),
        ("LAB-NET","Networks Lab","Block B",2,5,"linux;oscilloscope"),("LAB-EMB","Embedded Systems Lab","Block B",1,4,"fpga;oscilloscope;linux"),
        ("LAB-CAD","CAD & Fabrication Lab","Block C",1,6,"cad;3d-printer"),("LAB-VR","Immersive Media Lab","Block C",1,5,"vr-kit;gpu"),
        ("LAB-GEN1","General Computing 1","Block D",3,6,"linux;matlab"),("LAB-GEN2","General Computing 2","Block D",3,6,"linux")]
with open("labs.csv","w",newline="") as f:
    w = csv.writer(f); w.writerow(["code","name","building","capacity","max_team_size","equipment","status","reason"])
    for l in labs: w.writerow(list(l)+["AVAILABLE",""])
slots = []
for d in ["MON","TUE","WED","THU"]:
    for c,t,dur in [("09","09:00",120),("11","11:30",90),("14","14:00",120)]: slots.append((f"{d}-{c}",f"{d.title()} {t}",d,t,dur))
with open("slots.csv","w",newline="") as f:
    w = csv.writer(f); w.writerow(["code","label","day","start","duration_minutes","status"])
    for s in slots: w.writerow(list(s)+["AVAILABLE"])
bundles = [([],5),(["linux"],6),(["gpu"],5),(["gpu","linux"],4),(["fpga"],2),(["fpga","oscilloscope"],3),(["cad"],2),(["3d-printer"],1),
           (["cad","3d-printer"],2),(["vr-kit"],3),(["matlab"],2),(["linux","matlab"],3),(["linux","oscilloscope"],2)]
pool = [b for b,wt in bundles for _ in range(wt)]
codes = [s[0] for s in slots]
rows = []
for i in range(1, 101):
    req = r.choice(pool)
    size = r.choice([2,3,3,4,4,5,6]); dur = r.choice([60,90,90,120])
    el = [] if r.random() < .15 else sorted(r.sample(codes, r.randint(1,3)))
    rows.append([f"REQ-2026-{i:04d}", f"TEAM-{i:03d}", f"Team {i:03d}", size, ";".join(req), dur, ";".join(el)])
rows[-1][4] = "quantum-annealer"; rows[-2][4] = "quantum-annealer"   # two deliberately infeasible requests
with open("teams.csv","w",newline="") as f:
    w = csv.writer(f); w.writerow(["request_id","code","name","size","requirements","duration_minutes","eligible_slots"])
    w.writerows(rows)
