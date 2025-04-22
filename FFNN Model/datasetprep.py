import pandas as pd
import numpy as np
import random

# Define parameters
cities = ['Chennai', 'Delhi', 'Mumbai', 'Kolkata', 'Bangalore']
months = list(range(1, 13))
billing_cycles = list(range(1, 7))  # 1 to 6 months ahead

data = []

for _ in range(5000):  # 5000 samples
    city = random.choice(cities)
    last_paid_units = random.randint(100, 3000)
    billing_cycle = random.choice(billing_cycles)
    last_paid_month = random.choice(months)

    target_month = (last_paid_month + billing_cycle - 1) % 12 + 1

    # Heuristic for delta based on target_month
    if target_month in [3, 4, 5, 6]:  # Summer
        delta_percent = random.uniform(0.10, 0.30)
    elif target_month in [7, 8, 9]:  # Monsoon
        delta_percent = random.uniform(-0.05, 0.05)
    else:  # Winter
        delta_percent = random.uniform(-0.25, -0.10)

    delta_units = last_paid_units * delta_percent
    delta_units = round(delta_units, 2)

    data.append({
        "city": city,
        "last_paid_month": last_paid_month,
        "billing_cycle": billing_cycle,
        "last_paid_units": last_paid_units,
        "target_month": target_month,
        "delta_units": delta_units
    })

# Create DataFrame
df = pd.DataFrame(data)
df.to_csv("synthetic_prediction_dataset.csv", index=False)
print("✅ Synthetic dataset generated: synthetic_prediction_dataset.csv")
