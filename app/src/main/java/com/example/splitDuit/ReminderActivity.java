package com.example.splitDuit;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.button.MaterialButton;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ReminderActivity extends AppCompatActivity {

    private AppDatabase db;
    private boolean isOwesYouMode = true;
    private Debt individualDebt;
    private final ExecutorService databaseExecutor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reminder);

        // 1. Initialize all UI elements from your XML layout
        TextView txtReminderName = findViewById(R.id.txtReminderName);
        TextView txtReminderDetails = findViewById(R.id.txtReminderDetails);
        Button btnSendReminder = findViewById(R.id.btnSendReminder);
        Button btnMarkAsPaid = findViewById(R.id.btnMarkAsPaid);
        MaterialButton btnBack = findViewById(R.id.button);

        // 2. Open Room database connection
        db = AppDatabase.getInstance(this);

        // 3. Retrieve intent information passed from DebtTracker
        String name = getIntent().getStringExtra("NAME");
        isOwesYouMode = getIntent().getBooleanExtra("IS_OWES_YOU_MODE", true);

        if (name != null) {
            txtReminderName.setText(name);

            // 4. DATABASE READ: Must run in a background thread to prevent crashes!
            databaseExecutor.execute(() -> {
                individualDebt = db.debtDao().getDebtByName(isOwesYouMode, name);

                if (individualDebt != null) {
                    // Switch back to Main Thread to update the UI text
                    runOnUiThread(() -> {
                        if (isOwesYouMode) {
                            // Scenario: They owe you money
                            txtReminderDetails.setText(name + " hasn't paid\nRM " + individualDebt.totalPrice + "\nto you yet.");
                            btnSendReminder.setText("Send Reminder via WhatsApp");
                        } else {
                            // Scenario: You owe them money
                            txtReminderDetails.setText("You haven't paid\nRM " + individualDebt.totalPrice + "\nto " + name + " yet.");
                            btnSendReminder.setText("Share Payment Details");
                        }
                    });
                } else {
                    // Fallback visual display in case database row seeding was cleared
                    runOnUiThread(() -> {
                        txtReminderDetails.setText(isOwesYouMode
                                ? name + " hasn't paid\nRM 14.85\nto you yet."
                                : "You haven't paid\nRM 14.85\nto " + name + " yet.");
                    });
                }
            });
        }

        // 5. Set up the Back Button
        btnBack.setOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());

        // 6. Set up the Mark As Paid Button
        btnMarkAsPaid.setOnClickListener(v -> {
            // Update the state in Room Database first to ensure persistent data tracking integrity
            databaseExecutor.execute(() -> {
                if (individualDebt != null) {
                    individualDebt.status = "Paid";
                    db.debtDao().updateDebt(individualDebt);
                } else {
                    // Fallback handling: If the specific entry row wasn't cached, pull and update directly
                    Debt trackingRecord = db.debtDao().getDebtByName(isOwesYouMode, name);
                    if (trackingRecord != null) {
                        trackingRecord.status = "Paid";
                        db.debtDao().updateDebt(trackingRecord);
                    }
                }

                // Send layout configuration feedback status keys back upstream onto DebtTracker process stack
                runOnUiThread(() -> {
                    Intent returnIntent = new Intent();
                    returnIntent.putExtra("PASSED_NAME", name);
                    returnIntent.putExtra("IS_PAID", true);
                    setResult(RESULT_OK, returnIntent);

                    // Dynamic Toast notification feedback confirmation display
                    String toastMsg = isOwesYouMode ? "Marked as paid by " + name : "Marked as paid by you!";
                    Toast.makeText(ReminderActivity.this, toastMsg, Toast.LENGTH_SHORT).show();

                    finish();
                });
            });
        });

        // --- META CREDENTIALS ---
        String PHONE_NUMBER_ID = "1119586254576397";
        String ACCESS_TOKEN = "EAAWQCTUyTy4BRmqjFZBTsZBe7DI9WRjZAISYM146N8URiMabKZBZB1ZCCODWZBINjwuKI9mZAScRSu1JpoeUSHZBmhDB6CqrqVVqOMIYO7YXTZB41KWD3NvZChYreGysPQIeLv7bzLLrMtUCzSo1DGxQmVAHytvZA5qgUkaVoPHY3Ib2XGwdlZCgNuYKLQ3OLtvyOyXSHZA2xsZAwtZBfF6Ll6RGdS4cmUs0siKJstWdwp0TnHnbDvqHR3phcSSZBBnqh5BQlRdNIKiRDN5mJUTTDHOQ6OQ08nDOK";

        // 7. Set up the Send Reminder Button
        btnSendReminder.setOnClickListener(v -> {
            String RECIPIENT_PHONE = (individualDebt != null && individualDebt.friendPhoneNumber != null)
                    ? individualDebt.friendPhoneNumber
                    : "60182041535"; // Fallback

            String url = "https://graph.facebook.com/v18.0/" + PHONE_NUMBER_ID + "/messages";

            // Create JSON payload
            org.json.JSONObject postData = new org.json.JSONObject();
            try {
                postData.put("messaging_product", "whatsapp");
                postData.put("to", RECIPIENT_PHONE);
                postData.put("type", "template");

                org.json.JSONObject template = new org.json.JSONObject();
                template.put("name", "hello_world");

                org.json.JSONObject language = new org.json.JSONObject();
                language.put("code", "en_US");
                template.put("language", language);

                postData.put("template", template);
            } catch (Exception e) {
                e.printStackTrace();
            }

            com.android.volley.toolbox.JsonObjectRequest jsonObjectRequest = new com.android.volley.toolbox.JsonObjectRequest(
                    com.android.volley.Request.Method.POST, url, postData,
                    response -> Toast.makeText(ReminderActivity.this, "Message Sent!", Toast.LENGTH_LONG).show(),
                    error -> Toast.makeText(ReminderActivity.this, "Network or API Error", Toast.LENGTH_SHORT).show()
            ) {
                @Override
                public java.util.Map<String, String> getHeaders() {
                    java.util.Map<String, String> headers = new java.util.HashMap<>();
                    headers.put("Authorization", "Bearer " + ACCESS_TOKEN);
                    headers.put("Content-Type", "application/json");
                    return headers;
                }
            };
            com.android.volley.toolbox.Volley.newRequestQueue(this).add(jsonObjectRequest);
        });
    }
}