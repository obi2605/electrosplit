from flask import Flask, request, jsonify
import torch
import joblib
import numpy as np
from datetime import datetime
from model import FFNN

app = Flask(__name__)

# Load model and scalers
feature_cols = joblib.load("feature_columns.pkl")
units_scaler = joblib.load("units_scaler.pkl")
delta_scaler = joblib.load("delta_scaler.pkl")

model = FFNN(input_size=len(feature_cols))
model.load_state_dict(torch.load("ffnn_model.pth", map_location='cpu'))
model.eval()

@app.route('/predict', methods=['POST'])
def predict():
    data = request.get_json()
     # 🚨 Debugging: Print incoming request payload
    print("📥 Incoming Prediction Request:", data)

    city = data.get("city")
    last_paid_units = data.get("last_paid_units")
    last_paid_date = data.get("last_paid_date")
    billing_cycle = data.get("billing_cycle")

    try:
        last_paid_month = datetime.strptime(last_paid_date, "%Y-%m-%d").month
    except:
        return jsonify({"error": "Invalid date format"}), 400

    target_month = (last_paid_month + billing_cycle - 1) % 12 + 1

    # Build feature vector
    input_features = {
        'last_paid_month_norm': last_paid_month / 12,
        'billing_cycle_norm': billing_cycle / 6,
        'target_month_norm': target_month / 12,
        'last_paid_units_scaled': units_scaler.transform([[last_paid_units]])[0][0]
    }

    # Handle city one-hot
    for col in feature_cols:
        if col.startswith('city_'):
            input_features[col] = 1 if col == f'city_{city}' else 0

    if sum([input_features.get(c, 0) for c in feature_cols if c.startswith('city_')]) == 0:
        return jsonify({"error": "City not recognized"}), 400

    feature_vector = [input_features[col] for col in feature_cols]
    input_tensor = torch.tensor([feature_vector], dtype=torch.float32)

    with torch.no_grad():
        scaled_delta = model(input_tensor).item()

    delta_units = delta_scaler.inverse_transform([[scaled_delta]])[0][0]
    predicted_units = max(last_paid_units + delta_units, 0)

    return jsonify({
        "delta": round(delta_units, 2),
        "predicted_units": round(predicted_units, 2),
        "target_month": target_month
    })

if __name__ == "__main__":
    app.run(host="0.0.0.0", port=5000, debug=True)
