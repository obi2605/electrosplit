import pandas as pd
import torch
from torch import nn
import numpy as np
from torch.utils.data import DataLoader, TensorDataset
from sklearn.preprocessing import MinMaxScaler
import joblib

# Load dataset
df = pd.read_csv("synthetic_prediction_dataset.csv")

# One-hot encode city
df = pd.get_dummies(df, columns=['city'])

# Normalize features
scaler_units = MinMaxScaler()
df['last_paid_units_scaled'] = scaler_units.fit_transform(df[['last_paid_units']])

df['last_paid_month_norm'] = df['last_paid_month'] / 12
df['billing_cycle_norm'] = df['billing_cycle'] / 6
df['target_month_norm'] = df['target_month'] / 12

# Features & target
feature_cols = ['last_paid_month_norm', 'billing_cycle_norm', 'target_month_norm', 'last_paid_units_scaled'] + [col for col in df.columns if col.startswith('city_')]
X = df[feature_cols].values
y = df['delta_units'].values.reshape(-1, 1)

# Scale target
scaler_delta = MinMaxScaler()
y_scaled = scaler_delta.fit_transform(y)

# Convert to tensors
X_t = torch.tensor(X.astype(np.float32))
y_t = torch.tensor(y_scaled.astype(np.float32))

dataset = TensorDataset(X_t, y_t)
loader = DataLoader(dataset, batch_size=32, shuffle=True)

# Define FFNN
class FFNN(nn.Module):
    def __init__(self, input_size):
        super().__init__()
        self.net = nn.Sequential(
            nn.Linear(input_size, 64),
            nn.ReLU(),
            nn.Linear(64, 32),
            nn.ReLU(),
            nn.Linear(32, 1)
        )
    def forward(self, x):
        return self.net(x)

model = FFNN(input_size=X.shape[1])
optimizer = torch.optim.Adam(model.parameters(), lr=0.001)
criterion = nn.MSELoss()

# Train
epochs = 40
for epoch in range(epochs):
    total_loss = 0
    for xb, yb in loader:
        optimizer.zero_grad()
        loss = criterion(model(xb), yb)
        loss.backward()
        optimizer.step()
        total_loss += loss.item()
    print(f"Epoch {epoch+1}: Loss = {total_loss / len(loader):.4f}")

# Save model & scalers
torch.save(model.state_dict(), "ffnn_model.pth")
joblib.dump(scaler_units, "units_scaler.pkl")
joblib.dump(scaler_delta, "delta_scaler.pkl")
joblib.dump(feature_cols, "feature_columns.pkl")

print("✅ Model and scalers saved!")
