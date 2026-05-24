package com.example.splitDuit;

import android.app.DatePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SplitByItemReview extends AppCompatActivity {

    private TextView tvSplitSummary, tvTotalBillAmount, tvSubtotalAmount, tvTaxAmount, tvDueDateValue;
    private EditText etSplitNotes;
    private RecyclerView rvReviewList;
    private ImageButton btnBackReview;
    private Button btnSendRequest;
    private View btnDatePicker;

    private Calendar selectedCalendar;
    private List<PersonSummaryObject> dynamicSummaryList;
    private ReviewSummaryAdapter summaryAdapter;

    // --- Database Variables ---
    private AppDatabase db;
    private final ExecutorService databaseExecutor = Executors.newSingleThreadExecutor();
    private double finalSubtotalAmount = 0.0;
    private double finalTotalAmount = 0.0;
    private int currentUserId = 1; // Assuming default logged-in user is 1

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_split_by_item_review);

        // 1. Initialize Database
        db = AppDatabase.getInstance(this);

        // 2. Bind layouts
        tvSplitSummary = findViewById(R.id.tvSplitSummary);
        tvTotalBillAmount = findViewById(R.id.tvTotalBillAmount);
        tvSubtotalAmount = findViewById(R.id.tvSubtotalAmount);
        tvTaxAmount = findViewById(R.id.tvTaxAmount);
        tvDueDateValue = findViewById(R.id.tvDueDateValue);
        etSplitNotes = findViewById(R.id.etSplitNotes);
        rvReviewList = findViewById(R.id.rvReviewList);
        btnBackReview = findViewById(R.id.btnBackReview);
        btnSendRequest = findViewById(R.id.btnSendRequest);
        btnDatePicker = findViewById(R.id.btnDatePicker);

        // 3. Set Default Date
        selectedCalendar = Calendar.getInstance();
        tvDueDateValue.setText(new SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(selectedCalendar.getTime()));

        // 4. Calculate Split Amounts from Intent
        calculateLiveSplitsFromIntent();

        // 5. Setup RecyclerView Adapter
        rvReviewList.setLayoutManager(new LinearLayoutManager(this));
        summaryAdapter = new ReviewSummaryAdapter(dynamicSummaryList);
        rvReviewList.setAdapter(summaryAdapter);

        // 6. Click Listeners
        btnBackReview.setOnClickListener(v -> finish());
        btnDatePicker.setOnClickListener(v -> showDatePickerDialog());

        // Save to database FIRST, then navigate
        btnSendRequest.setOnClickListener(v -> saveSplitDataToDatabase());
    }

    private void calculateLiveSplitsFromIntent() {
        dynamicSummaryList = new ArrayList<>();

        List<SplitItem.BillItemObject> billItems =
                (List<SplitItem.BillItemObject>) getIntent().getSerializableExtra("BILL_ITEMS_DATA");

        if (billItems == null || billItems.isEmpty()) {
            return;
        }

        finalSubtotalAmount = 0.0;
        Map<String, Double> itemsPriceMap = new HashMap<>();
        Map<String, Integer> itemsCountMap = new HashMap<>();

        for (SplitItem.BillItemObject item : billItems) {
            finalSubtotalAmount += item.price;

            String person = item.assignedPersonName;
            if (person == null || person.trim().isEmpty() || person.trim().equalsIgnoreCase("Assign")) {
                person = "Unassigned";
            }

            itemsPriceMap.put(person, itemsPriceMap.getOrDefault(person, 0.0) + item.price);
            itemsCountMap.put(person, itemsCountMap.getOrDefault(person, 0) + 1);
        }

        // Apply 6% Tax calculations
        double tax = finalSubtotalAmount * 0.06;
        finalTotalAmount = finalSubtotalAmount + tax;

        tvSubtotalAmount.setText(String.format(Locale.US, "RM %.2f", finalSubtotalAmount));
        tvTaxAmount.setText(String.format(Locale.US, "RM %.2f", tax));
        tvTotalBillAmount.setText(String.format(Locale.US, "RM %.2f", finalTotalAmount));

        for (String name : itemsPriceMap.keySet()) {
            double baseAmount = itemsPriceMap.get(name);
            double proportionalTaxValue = baseAmount * 0.06;
            double finalizedOwedCost = baseAmount + proportionalTaxValue;

            dynamicSummaryList.add(new PersonSummaryObject(name, itemsCountMap.get(name), finalizedOwedCost));
        }

        tvSplitSummary.setText("Split between " + dynamicSummaryList.size() + " people");
    }

    private void saveSplitDataToDatabase() {
        String transactionNotes = etSplitNotes.getText().toString().trim();
        if (transactionNotes.isEmpty()) {
            transactionNotes = "Split Bill"; // Acts as shopName
        }

        final String finalNotes = transactionNotes;
        final String finalDueDate = tvDueDateValue.getText().toString();

        // Run database operations on a background thread
        databaseExecutor.execute(() -> {
            try {
                // 1. Insert Receipt Master Record using YOUR EXACT Receipt constructor
                // Receipt(int userId, String shopName, String item, double price, double totalPrice)
                Receipt newReceipt = new Receipt(
                        currentUserId,
                        finalNotes,                 // Using notes as shopName
                        "Split Items Summary",      // Generic item description for the master receipt
                        finalSubtotalAmount,        // Raw price
                        finalTotalAmount            // Price with tax
                );

                long generatedReceiptId = db.receiptDao().insertReceipt(newReceipt);

                // 2. Loop through each person in the split summary
                for (PersonSummaryObject summaryItem : dynamicSummaryList) {
                    // Skip 'Unassigned' placeholder
                    if (summaryItem.personName.equalsIgnoreCase("Unassigned")) continue;

                    // 3. Find or Create Friend
                    Friend existingFriend = db.friendDao().getFriendByName(summaryItem.personName);
                    int assignedFriendId;

                    if (existingFriend != null) {
                        assignedFriendId = existingFriend.friendId;
                        existingFriend.date = selectedCalendar.getTime();
                        db.friendDao().updateFriend(existingFriend);
                    } else {
                        Friend newFriend = new Friend(summaryItem.personName, null, selectedCalendar.getTime());
                        newFriend.note = finalNotes;
                        long generatedFriendId = db.friendDao().insertFriend(newFriend);
                        assignedFriendId = (int) generatedFriendId;
                    }

                    // 4. Create and Insert Debt Record
                    // Update this block near line 184 in SplitByItemReview.java
                    // 4. Create and Insert Debt Record
                    Debt trackingDebtRecord = new Debt(
                            currentUserId,
                            (int) generatedReceiptId,   // <-- Fixes receiptId compiler symbol error
                            assignedFriendId,           // <-- Fixes friendId compiler symbol error
                            summaryItem.personName,     // <-- Maps to the actual loop entity name
                            "Unpaid",
                            true,                       // isOwesYou Mode defaults to true
                            summaryItem.totalOwedAmount,// <-- Maps to the actual loop entity cost field
                            null,                       // No phone available inside summaryItem list loop
                            selectedCalendar.getTimeInMillis() // Pass the deadline set by the user's DatePicker
                    );
                    db.debtDao().insertDebt(trackingDebtRecord);
                }

                // 5. Navigate to SendRequest Activity on the Main UI Thread
                runOnUiThread(() -> {
                    Intent intent = new Intent(SplitByItemReview.this, SendRequest.class);
                    intent.putExtra("TRANSACTION_NOTES", finalNotes);
                    intent.putExtra("FINAL_DUE_DATE", finalDueDate);
                    intent.putExtra("BILL_SUMMARY_DATA", new ArrayList<>(dynamicSummaryList));
                    startActivity(intent);
                });

            } catch (Exception err) {
                err.printStackTrace();
                runOnUiThread(() -> Toast.makeText(SplitByItemReview.this, "Database entry failed!", Toast.LENGTH_LONG).show());
            }
        });
    }

    private void showDatePickerDialog() {
        DatePickerDialog dialog = new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
            selectedCalendar.set(Calendar.YEAR, year);
            selectedCalendar.set(Calendar.MONTH, month);
            selectedCalendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);
            tvDueDateValue.setText(new SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(selectedCalendar.getTime()));
        }, selectedCalendar.get(Calendar.YEAR), selectedCalendar.get(Calendar.MONTH), selectedCalendar.get(Calendar.DAY_OF_MONTH));
        dialog.show();
    }

    // --- DATA CAPSULE ENTITY ---
    public static class PersonSummaryObject implements java.io.Serializable {
        private static final long serialVersionUID = 1L;

        public String personName;
        public int itemsCount;
        public double totalOwedAmount;

        public PersonSummaryObject(String personName, int itemsCount, double totalOwedAmount) {
            this.personName = personName;
            this.itemsCount = itemsCount;
            this.totalOwedAmount = totalOwedAmount;
        }
    }

    // --- ADAPTER ELEMENT ---
    public static class ReviewSummaryAdapter extends RecyclerView.Adapter<ReviewSummaryAdapter.ViewHolder> {
        private final List<PersonSummaryObject> list;
        public ReviewSummaryAdapter(List<PersonSummaryObject> list) { this.list = list; }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LinearLayout row = new LinearLayout(parent.getContext());
            row.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(parent.getContext(), 60)));
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);

            CardView avatarCard = new CardView(parent.getContext());
            avatarCard.setLayoutParams(new LinearLayout.LayoutParams(dpToPx(parent.getContext(), 40), dpToPx(parent.getContext(), 40)));
            avatarCard.setRadius(dpToPx(parent.getContext(), 20));
            avatarCard.setCardBackgroundColor(0xFFF2EFF7);
            avatarCard.setCardElevation(0);

            TextView tvBadge = new TextView(parent.getContext());
            tvBadge.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            tvBadge.setGravity(Gravity.CENTER);
            tvBadge.setTextColor(0xFF5A189A);
            tvBadge.setTypeface(null, Typeface.BOLD);
            avatarCard.addView(tvBadge);
            row.addView(avatarCard);

            LinearLayout textualWrapper = new LinearLayout(parent.getContext());
            textualWrapper.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams wrapParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
            wrapParams.setMarginStart(dpToPx(parent.getContext(), 12));
            textualWrapper.setLayoutParams(wrapParams);

            TextView tvProfileName = new TextView(parent.getContext());
            tvProfileName.setTextColor(0xFF1C1B1F);
            tvProfileName.setTypeface(null, Typeface.BOLD);
            textualWrapper.addView(tvProfileName);

            TextView tvItemsSummaryCountText = new TextView(parent.getContext());
            tvItemsSummaryCountText.setTextColor(0xFF7A757F);
            textualWrapper.addView(tvItemsSummaryCountText);
            row.addView(textualWrapper);

            TextView tvOwedAmountValueText = new TextView(parent.getContext());
            tvOwedAmountValueText.setTextColor(0xFF5A189A);
            tvOwedAmountValueText.setTypeface(null, Typeface.BOLD);
            row.addView(tvOwedAmountValueText);

            return new ViewHolder(row, tvBadge, tvProfileName, tvItemsSummaryCountText, tvOwedAmountValueText);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            PersonSummaryObject entity = list.get(position);
            holder.tvProfileName.setText(entity.personName);
            holder.tvItemsSummaryCountText.setText(entity.itemsCount + " " + (entity.itemsCount > 1 ? "items" : "item"));
            holder.tvOwedAmountValueText.setText(String.format(Locale.US, "RM %.2f", entity.totalOwedAmount));
            if (entity.personName != null && !entity.personName.isEmpty()) {
                holder.tvBadge.setText(String.valueOf(entity.personName.charAt(0)).toUpperCase());
            }
        }

        @Override
        public int getItemCount() { return list.size(); }
        private int dpToPx(Context c, int dp) { return (int) (dp * c.getResources().getDisplayMetrics().density); }

        public static class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvBadge, tvProfileName, tvItemsSummaryCountText, tvOwedAmountValueText;
            public ViewHolder(@NonNull View itemView, TextView tvBadge, TextView tvProfileName, TextView tvItemsSummaryCountText, TextView tvOwedAmountValueText) {
                super(itemView);
                this.tvBadge = tvBadge;
                this.tvProfileName = tvProfileName;
                this.tvItemsSummaryCountText = tvItemsSummaryCountText;
                this.tvOwedAmountValueText = tvOwedAmountValueText;
            }
        }
    }
}