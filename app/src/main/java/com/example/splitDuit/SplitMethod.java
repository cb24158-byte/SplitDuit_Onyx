package com.example.splitDuit;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SplitMethod extends AppCompatActivity {

    ImageView btnBack;
    TextView txtTotalAmount;
    RadioButton radioEqual, radioItems;
    CardView cardEqual, cardItems;
    LinearLayout layoutEqual, layoutItems;

    TextView txtEqualTitle, txtEqualDesc;
    TextView txtItemsTitle, txtItemsDesc;

    Button btnNext;
    String selectedMethod = "";

    // --- DATABASE PIPELINE FIELDS ---
    private AppDatabase db;
    private final ExecutorService databaseExecutor = Executors.newSingleThreadExecutor();
    private int currentUserId;
    private int savedReceiptId; // Receives the receipt integer hash code from ScanReceipt
    private double databaseCalculatedTotal = 0.00;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_split_method);

        // Initialize Database and capture Intent Pipeline tracking IDs
        db = AppDatabase.getInstance(this);
        currentUserId = getIntent().getIntExtra("LOGGED_IN_USER_ID", 1);
        savedReceiptId = getIntent().getIntExtra("SAVED_RECEIPT_ID", -1);

        btnBack = findViewById(R.id.btnBack);
        txtTotalAmount = findViewById(R.id.txtTotalAmount);

        radioEqual = findViewById(R.id.radioEqual);
        radioItems = findViewById(R.id.radioItems);

        cardEqual = findViewById(R.id.cardEqual);
        cardItems = findViewById(R.id.cardItems);

        layoutEqual = findViewById(R.id.layoutEqual);
        layoutItems = findViewById(R.id.layoutItems);

        txtEqualTitle = findViewById(R.id.txtEqualTitle);
        txtEqualDesc = findViewById(R.id.txtEqualDesc);

        txtItemsTitle = findViewById(R.id.txtItemsTitle);
        txtItemsDesc = findViewById(R.id.txtItemsDesc);

        btnNext = findViewById(R.id.btnNext);

        clearSelection();

        // 🔥 READ DIRECTLY FROM ROOM DATABASE FOR DISPLAY TOTAL
        loadReceiptTotalFromDatabase();

        cardEqual.setOnClickListener(v -> {
            clearSelection();
            radioEqual.setChecked(true);
            selectedMethod = "Equal Split";
            layoutEqual.setBackgroundColor(Color.parseColor("#E9D5FF"));
            txtEqualTitle.setTextColor(Color.parseColor("#5A189A"));
            txtEqualDesc.setTextColor(Color.BLACK);
        });

        cardItems.setOnClickListener(v -> {
            clearSelection();
            radioItems.setChecked(true);
            selectedMethod = "Split by Items";
            layoutItems.setBackgroundColor(Color.parseColor("#E9D5FF"));
            txtItemsTitle.setTextColor(Color.parseColor("#5A189A"));
            txtItemsDesc.setTextColor(Color.BLACK);
        });

        btnNext.setOnClickListener(v -> {
            // Guard Check: Fallback if user clicked radio circle directly instead of card frame
            if (selectedMethod.isEmpty()) {
                if (radioEqual.isChecked()) selectedMethod = "Equal Split";
                else if (radioItems.isChecked()) selectedMethod = "Split by Items";
            }

            if (selectedMethod.isEmpty()) {
                Toast.makeText(SplitMethod.this, "Please select a split method", Toast.LENGTH_SHORT).show();
            } else {
                Intent intent;
                if (radioItems.isChecked()) {
                    intent = new Intent(SplitMethod.this, SplitItem.class);
                } else {
                    intent = new Intent(SplitMethod.this, SelectFriends.class);
                }

                // Pass down your pipeline data to the subsequent screens
                intent.putExtra("LOGGED_IN_USER_ID", currentUserId);
                intent.putExtra("SAVED_RECEIPT_ID", savedReceiptId);
                intent.putExtra("TOTAL_AMOUNT", String.format(Locale.US, "RM %.2f", databaseCalculatedTotal));

                startActivity(intent);
            }
        });

        btnBack.setOnClickListener(v -> finish());
    }

    private void loadReceiptTotalFromDatabase() {
        databaseExecutor.execute(() -> {
            try {
                // 1. Fetch ALL receipt records matching the logged-in user
                List<Receipt> allUserReceipts = db.receiptDao().getAllReceiptsForUser(currentUserId);

                if (allUserReceipts != null) {
                    // 2. Loop through rows and find the matching receipt session string via hashcode match
                    // 🔥 REPLACE THAT FOR-LOOP INSIDE YOUR BLOCK WITH THIS ONE:
                    for (Receipt r : allUserReceipts) {
                        if (r.shopName != null) {

                            // Check 1: Does the hashcode match? (For ScanReceipt)
                            boolean isScanReceiptMatch = (r.shopName.hashCode() == savedReceiptId);

                            // Check 2: Does the raw text string match? (For ManualAmount)
                            boolean isManualMatch = r.shopName.equals(String.valueOf(savedReceiptId));

                            if (isScanReceiptMatch || isManualMatch) {
                                databaseCalculatedTotal = r.totalPrice;

                                // Backup Fallback calculation
                                if (databaseCalculatedTotal <= 0) {
                                    for (Receipt subItem : allUserReceipts) {
                                        if (subItem.shopName != null && subItem.shopName.equals(r.shopName)) {
                                            databaseCalculatedTotal += subItem.price;
                                        }
                                    }
                                }
                                break; // Match found, exit loop safely
                            }
                        }
                    }

                }

                // Switch back to Main UI Thread to update the text display
                runOnUiThread(() -> {
                    if (databaseCalculatedTotal > 0) {
                        txtTotalAmount.setText(String.format(Locale.US, "RM %.2f", databaseCalculatedTotal));
                    } else {
                        useIntentFallbackAmount();
                    }
                });

            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(this::useIntentFallbackAmount);
            }
        });
    }

    private void useIntentFallbackAmount() {
        String totalAmountIntent = getIntent().getStringExtra("TOTAL_AMOUNT");
        if (totalAmountIntent != null && !totalAmountIntent.isEmpty()) {
            txtTotalAmount.setText(totalAmountIntent);
            try {
                String cleanNum = totalAmountIntent.replace("RM", "").trim();
                databaseCalculatedTotal = Double.parseDouble(cleanNum);
            } catch (Exception ignored) {}
        } else {
            txtTotalAmount.setText("RM 0.00");
        }
    }

    private void clearSelection() {
        radioEqual.setChecked(false);
        radioItems.setChecked(false);

        layoutEqual.setBackgroundColor(Color.parseColor("#F5F5F5"));
        layoutItems.setBackgroundColor(Color.parseColor("#F5F5F5"));

        txtEqualTitle.setTextColor(Color.parseColor("#1C1B1F"));
        txtEqualDesc.setTextColor(Color.parseColor("#7A757F"));

        txtItemsTitle.setTextColor(Color.parseColor("#1C1B1F"));
        txtItemsDesc.setTextColor(Color.parseColor("#7A757F"));
    }
}