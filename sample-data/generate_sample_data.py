"""
Generate 6 months of sample sales CSV files for acmecorp tenant.
Run: python3 generate_sample_data.py
"""
import csv, random, os
from datetime import date, timedelta

TENANT = "acmecorp"
CATEGORIES = ["Electronics", "Clothing", "Home & Garden", "Sports"]
LOCATIONS = [
    ("San Francisco", "CA"), ("Los Angeles", "CA"), ("New York", "NY"),
    ("Chicago", "IL"), ("Austin", "TX"), ("Dallas", "TX"), ("Seattle", "WA"),
]

BASE_REVENUE = {
    "Electronics":   (3500, 6500),
    "Clothing":      (900,  1800),
    "Home & Garden": (650,  1200),
    "Sports":        (1700, 3000),
}
BASE_UNITS = {
    "Electronics": (10, 20), "Clothing": (7, 14),
    "Home & Garden": (5, 10), "Sports": (8, 17),
}

SEASON = {10: 0.85, 11: 1.05, 12: 1.30, 1: 0.75, 2: 0.80}

MONTHS = [
    (2025, 10, "october"),
    (2025, 11, "november"),
    (2025, 12, "december"),
    (2026,  1, "january"),
    (2026,  2, "february"),
    (2026,  4, "april"),
]

outdir = os.path.dirname(os.path.abspath(__file__))

for (yr, mo, name) in MONTHS:
    random.seed(yr * 100 + mo)
    mult = SEASON.get(mo, 1.0)

    start = date(yr, mo, 1)
    end = date(yr + (1 if mo == 12 else 0), (mo % 12) + 1, 1)

    rows = []
    d = start
    while d < end:
        for cat in CATEGORIES:
            city, region = random.choice(LOCATIONS)
            lo, hi = BASE_REVENUE[cat]
            revenue = round(random.uniform(lo * mult, hi * mult) * random.uniform(0.9, 1.1), 2)
            ulo, uhi = BASE_UNITS[cat]
            units = random.randint(max(1, int(ulo * mult)), max(2, int(uhi * mult) + 1))
            rows.append([TENANT, d.isoformat(), cat, city, region, revenue, units])
        d += timedelta(days=1)

    fname = f"sales_{name}_{yr}.csv"
    fpath = os.path.join(outdir, fname)
    with open(fpath, "w", newline="") as f:
        w = csv.writer(f)
        w.writerow(["tenant_id", "transaction_date", "category_name", "city", "region", "total_revenue", "units_sold"])
        w.writerows(rows)
    print(f"Created {fname}: {len(rows)} rows")
