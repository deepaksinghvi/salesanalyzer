# Sample Data

This folder contains sample CSV files for the **acmecorp** tenant used for local development, testing, and demo purposes.

---

## Tenant

All files are pre-seeded for the `acmecorp` tenant:

| Field | Value |
|---|---|
| Login | `admin@acmecorp.com` / `admin` |

---

## CSV Format

All files share the same schema expected by the upload API:

```
tenant_id, transaction_date, category_name, city, region, total_revenue, units_sold
```

| Column | Type | Description |
|---|---|---|
| `tenant_id` | UUID | Must match an existing tenant in the database |
| `transaction_date` | `YYYY-MM-DD` | Date of the sales transaction |
| `category_name` | string | One of: `Electronics`, `Clothing`, `Home & Garden`, `Sports` |
| `city` | string | City where the sale occurred |
| `region` | string | US state abbreviation (e.g. `CA`, `NY`) |
| `total_revenue` | decimal | Revenue for that day/category/location in USD |
| `units_sold` | integer | Number of units sold |

---

## Files

| File | Month | Rows | Seasonal Factor | Notes |
|---|---|---|---|---|
| `sales_october_2025.csv` | Oct 2025 | 124 | Low (0.85×) | Start of Q4, pre-holiday |
| `sales_november_2025.csv` | Nov 2025 | 120 | Moderate (1.05×) | Pre-Thanksgiving uptick |
| `sales_december_2025.csv` | Dec 2025 | 124 | Peak (1.30×) | Holiday season — highest revenue |
| `sales_january_2026.csv` | Jan 2026 | 124 | Low (0.75×) | Post-holiday dip |
| `sales_february_2026.csv` | Feb 2026 | 112 | Low (0.80×) | Short month, slow period |
| `sales_march_2026.csv` | Mar 2026 | 122 | Normal (1.0×) | Hand-crafted baseline file |
| `sales_april_2026.csv` | Apr 2026 | 120 | Normal (1.0×) | For testing forecast vs actuals overlap |

---

## Recommended Upload Order

Upload oldest-to-newest for accurate trend analysis and forecasting:

```
1. sales_october_2025.csv
2. sales_november_2025.csv
3. sales_december_2025.csv
4. sales_january_2026.csv
5. sales_february_2026.csv
6. sales_march_2026.csv
```

> Upload `sales_april_2026.csv` only if you want to test a scenario where actuals exist
> for the same month that was previously forecasted.

---

## Forecast Horizon Behaviour

The forecaster dynamically computes the horizon as the exact number of days in the **next calendar month** after the latest actual data:

| Latest Actual Month | Forecast Output |
|---|---|
| Jan 2026 (31 days of actuals) | Feb 2026 (28 days) |
| Feb 2026 | Mar 2026 (31 days) |
| Dec 2025 | Jan 2026 (31 days) |

Each forecast run produces exactly **one month** of predictions.

---

## Regenerating Data

To regenerate or customise the CSV files, run:

```bash
cd sample-data
python3 generate_sample_data.py
```

Edit `generate_sample_data.py` to adjust revenue ranges, seasonal multipliers, categories, or locations.

---

## How to Upload

1. Log in to the UI at `http://localhost:5173`
2. Navigate to **Upload** in the sidebar
3. Select the CSV file and choose the matching period type
4. The Temporal workflow will process it and refresh the materialized view automatically
5. After uploading all months, click **Run Forecast** on the Dashboard to generate predictions
