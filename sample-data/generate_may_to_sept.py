"""
Generate a single CSV with May-Sep 2025 data for acmecorp tenant.
Run: python3 generate_may_to_sept.py
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

# Seasonal multipliers for May-Sep
SEASON = {5: 0.90, 6: 0.95, 7: 1.10, 8: 1.05, 9: 0.92}

MONTHS = [
    (2026, 5, "may"),
    (2026, 6, "june"),
    (2026, 7, "july"),
    (2026, 8, "august"),
    (2026, 9, "september"),
]

outdir = os.path.dirname(os.path.abspath(__file__))
all_rows = []

for (yr, mo, name) in MONTHS:
    random.seed(yr * 100 + mo)
    mult = SEASON.get(mo, 1.0)

    start = date(yr, mo, 1)
    end = date(yr + (1 if mo == 12 else 0), (mo % 12) + 1, 1)

    d = start
    while d < end:
        for cat in CATEGORIES:
            city, region = random.choice(LOCATIONS)
            lo, hi = BASE_REVENUE[cat]
            revenue = round(random.uniform(lo * mult, hi * mult) * random.uniform(0.9, 1.1), 2)
            ulo, uhi = BASE_UNITS[cat]
            units = random.randint(max(1, int(ulo * mult)), max(2, int(uhi * mult) + 1))
            all_rows.append([TENANT, d.isoformat(), cat, city, region, revenue, units])
        d += timedelta(days=1)

fname = "sales_may_to_sept_2026.csv"
fpath = os.path.join(outdir, fname)
with open(fpath, "w", newline="") as f:
    w = csv.writer(f)
    w.writerow(["tenant_id", "transaction_date", "category_name", "city", "region", "total_revenue", "units_sold"])
    w.writerows(all_rows)
print(f"Created {fname}: {len(all_rows)} rows")
