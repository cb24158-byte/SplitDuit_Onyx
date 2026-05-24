package com.example.splitDuit;

import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.Locale;

public class ManualAmount extends AppCompatActivity {

    ImageButton btnBack;

    EditText edtItemName;
    EditText edtPrice;
    EditText edtTax;

    TextView txtTotal;

    Button btnAddItem;
    Button btnNext;

    LinearLayout layoutItems;

    double subtotal = 0;

    private AppDatabase db;
    private final ExecutorService databaseExecutor = Executors.newSingleThreadExecutor();
    private int currentUserId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_manual_amount);

        db = AppDatabase.getInstance(this);

        // Retrieve logged-in user ID passed down the pipeline
        currentUserId = getIntent().getIntExtra("LOGGED_IN_USER_ID", 1);

        btnBack = findViewById(R.id.btnBack);

        edtItemName = findViewById(R.id.edtItemName);
        edtPrice = findViewById(R.id.edtPrice);
        edtTax = findViewById(R.id.edtTax);

        txtTotal = findViewById(R.id.txtTotal);

        btnAddItem = findViewById(R.id.btnAddItem);
        btnNext = findViewById(R.id.btnNext);

        layoutItems = findViewById(R.id.layoutItems);

        // ADD ITEM
        btnAddItem.setOnClickListener(v -> addItem());

        // UPDATE TOTAL WHEN TAX CHANGES
        edtTax.setOnFocusChangeListener((v, hasFocus) -> {

            if (!hasFocus) {

                updateTotal();
            }
        });

        // NEXT BUTTON
        btnNext.setOnClickListener(v -> {

            double total = calculateTotal();

            if (total <= 0) {

                Toast.makeText(
                        this,
                        "Please add item first",
                        Toast.LENGTH_SHORT
                ).show();

                return;
            }
            btnNext.setEnabled(false);
            btnNext.setText("Saving...");

            saveManualReceiptToDatabase(total);
        });

        // BACK BUTTON
        btnBack.setOnClickListener(v -> finish());
    }

    private void saveManualReceiptToDatabase(double totalBillAmount) {
        databaseExecutor.execute(() -> {
            try {
                int uniqueReceiptSessionId = -1;
                boolean isFirstItemSaved = false;

                // Loop through the UI views added to layoutItems
                for (int i = 0; i < layoutItems.getChildCount(); i++) {
                    // Check if the child is our item row (ignore the header TextView at index 0)
                    if (layoutItems.getChildAt(i) instanceof LinearLayout) {
                        LinearLayout row = (LinearLayout) layoutItems.getChildAt(i);
                        TextView txtName = (TextView) row.getChildAt(0);
                        TextView txtPrice = (TextView) row.getChildAt(1);

                        String itemNameStr = txtName.getText().toString();
                        // Strip out "RM" labels to clean up the double parsing process
                        String priceStr = txtPrice.getText().toString().replace("RM", "").trim();

                        try {
                            double parsedPrice = Double.parseDouble(priceStr);

                            // Construct a separate Receipt row item record
                            Receipt manualReceiptItem = new Receipt(
                                    currentUserId,
                                    "",     // shopName
                                    itemNameStr,        // specific item name
                                    parsedPrice,        // individual item price
                                    totalBillAmount     // grand total of bill layout
                            );

                            long insertedId = db.receiptDao().insertReceipt(manualReceiptItem);

                            if (!isFirstItemSaved) {
                                uniqueReceiptSessionId = (int) insertedId;
                                isFirstItemSaved = true;
                            }
                        } catch (NumberFormatException ignored) {}
                    }
                }

                final int finalReceiptId = uniqueReceiptSessionId;

                // Return back to UI thread to navigate to the SplitMethod screen
                runOnUiThread(() -> {
                    Toast.makeText(ManualAmount.this, "Bill saved successfully!", Toast.LENGTH_SHORT).show();

                    Intent intent = new Intent(ManualAmount.this, SplitMethod.class);
                    intent.putExtra("LOGGED_IN_USER_ID", currentUserId);
                    intent.putExtra("SAVED_RECEIPT_ID", finalReceiptId);
                    intent.putExtra("TOTAL_AMOUNT", String.format(Locale.US, "RM %.2f", totalBillAmount));

                    startActivity(intent);
                    finish();
                });

            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    Toast.makeText(ManualAmount.this, "Failed to save data.", Toast.LENGTH_SHORT).show();
                    btnNext.setEnabled(true);
                    btnNext.setText("Next");
                });
            }
        });
    }

    // ADD ITEM
    private void addItem() {

        String itemName = edtItemName.getText().toString().trim();

        double price = getValue(edtPrice.getText().toString());

        if (itemName.isEmpty()) {

            Toast.makeText(
                    this,
                    "Enter item name",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        if (price <= 0) {

            Toast.makeText(
                    this,
                    "Enter item price",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        subtotal += price;

        addItemRow(itemName, price);

        updateTotal();

        edtItemName.setText("");
        edtPrice.setText("");
    }

    // ADD ITEM ROW
    private void addItemRow(String item, double price) {

        // MAIN ROW
        LinearLayout row = new LinearLayout(this);

        row.setOrientation(LinearLayout.HORIZONTAL);

        row.setPadding(0,20,0,20);

        row.setGravity(Gravity.CENTER_VERTICAL);

        // ITEM NAME
        TextView txtItem = new TextView(this);

        txtItem.setText(item);

        txtItem.setTextSize(18);

        txtItem.setTextColor(
                getResources().getColor(android.R.color.black)
        );

        txtItem.setLayoutParams(
                new LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1
                )
        );

        // PRICE
        TextView txtPrice = new TextView(this);

        txtPrice.setText(
                String.format(
                        Locale.getDefault(),
                        "RM %.2f",
                        price
                )
        );

        txtPrice.setTextSize(18);

        txtPrice.setTextColor(
                getResources().getColor(android.R.color.black)
        );

        txtPrice.setPadding(0,0,25,0);

        // DELETE ICON
        ImageView btnDelete = new ImageView(this);

        btnDelete.setImageResource(
                android.R.drawable.ic_menu_delete
        );

        btnDelete.setColorFilter(
                getResources().getColor(android.R.color.holo_red_dark)
        );

        btnDelete.setPadding(10,10,10,10);

        // DELETE FUNCTION
        btnDelete.setOnClickListener(v -> {

            subtotal -= price;

            layoutItems.removeView(row);

            updateTotal();

            Toast.makeText(
                    ManualAmount.this,
                    item + " removed",
                    Toast.LENGTH_SHORT
            ).show();
        });

        // ADD INTO ROW
        row.addView(txtItem);

        row.addView(txtPrice);

        row.addView(btnDelete);

        // ADD ROW TO LAYOUT
        layoutItems.addView(row);
    }

    // CALCULATE TOTAL
    private double calculateTotal() {

        double taxPercent = getValue(edtTax.getText().toString());

        double taxAmount = subtotal * (taxPercent / 100);

        return subtotal + taxAmount;
    }

    // UPDATE TOTAL
    private void updateTotal() {
        double total = calculateTotal();

        txtTotal.setText(
                String.format(
                        Locale.getDefault(),
                        "RM %.2f",
                        total
                )
        );
    }

    // CONVERT STRING TO DOUBLE
    private double getValue(String text) {

        if (text.isEmpty()) {

            return 0;
        }

        return Double.parseDouble(text);
    }
}