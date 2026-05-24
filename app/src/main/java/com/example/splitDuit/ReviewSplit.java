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
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ReviewSplit extends AppCompatActivity {

    private AppDatabase db;
    private final ExecutorService databaseExecutor = Executors.newSingleThreadExecutor();
    private int currentUserId = 1;
    private TextView tvSplitSummary;
    private TextView tvTotalAmountDisplay;
    private RecyclerView rvReviewList;
    private ImageButton btnBackReview;
    private Button btnSendRequest;

    private View btnDatePicker;
    private TextView tvDueDateValue;
    private Calendar selectedCalendar;

    private List<String> selectedFriends;
    private List<Double> individualShares;
    private double totalAmount;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_review_split);

        db = AppDatabase.getInstance(this);

        tvSplitSummary = findViewById(R.id.tvSplitSummary);
        tvTotalAmountDisplay = findViewById(R.id.totalAmount);
        rvReviewList = findViewById(R.id.rvReviewList);
        btnBackReview = findViewById(R.id.btnBackReview);
        btnSendRequest = findViewById(R.id.btnSendRequest);

        btnDatePicker = findViewById(R.id.btnDatePicker);
        tvDueDateValue = findViewById(R.id.tvDueDateValue);
        selectedCalendar = Calendar.getInstance();

        currentUserId = getIntent().getIntExtra("LOGGED_IN_USER_ID", 1);

        String totalAmountStr = getIntent().getStringExtra("TOTAL_AMOUNT");
        if (totalAmountStr != null) {
            try {
                totalAmount = Double.parseDouble(totalAmountStr.replace("RM", "").trim());
            } catch (NumberFormatException e) {
                totalAmount = 0.0;
            }
        } else {
            totalAmount = getIntent().getDoubleExtra("TOTAL_AMOUNT", 0.0);
        }

        tvTotalAmountDisplay.setText(String.format(Locale.US, "RM %.2f", totalAmount));

        selectedFriends = getIntent().getStringArrayListExtra("SELECTED_FRIENDS");
        if (selectedFriends == null) {
            selectedFriends = new ArrayList<>();
        }

        if (!selectedFriends.contains("You")) {
            selectedFriends.add(0, "You");
        }

        tvSplitSummary.setText("Split between " + selectedFriends.size() + " people");
        double baselineShare = selectedFriends.isEmpty() ? 0.0 : (totalAmount / selectedFriends.size());

        individualShares = new ArrayList<>();
        for (int i = 0; i < selectedFriends.size(); i++) {
            individualShares.add(baselineShare);
        }

        rvReviewList.setLayoutManager(new LinearLayoutManager(this));
        rvReviewList.setAdapter(new ReviewAdapter(selectedFriends, individualShares));

        btnBackReview.setOnClickListener(v -> finish());
        btnDatePicker.setOnClickListener(v -> showDatePickerDialog());
        btnSendRequest.setOnClickListener(v -> saveSplitToDatabase());
    }

    private void saveSplitToDatabase() {
        // 1. Grab the EditText for the description note
        EditText etNote = findViewById(R.id.note);
        String userNote = "";
        if (etNote != null) {
            userNote = etNote.getText().toString().trim();
        }

        // Fallback default if the user left the note field completely empty
        if (userNote.isEmpty()) {
            userNote = "Dinner Split Transaction";
        }

        // 2. Determine the source type dynamically based on how we got here
        // If it came from ScanReceipt, we can mark it; otherwise, it's a Manual Entry split
        String sourceType = getIntent().hasExtra("SAVED_RECEIPT_ID") ? "AI Camera Scan" : "Manual Entry";

        final String finalItemNote = userNote;
        final String finalShopName = sourceType;

        // 3. Save to database using the actual variable data
        databaseExecutor.execute(() -> {
            try {
                // NO MORE HARDCODING: Using the real data captured from the user input pipeline!
                Receipt newReceipt = new Receipt(
                        currentUserId,         // userId (int)
                        finalShopName,         // shopName -> "AI Camera Scan" or "Manual Entry"
                        finalItemNote,         // item -> The actual note input from the user
                        totalAmount,           // price (double)
                        totalAmount            // totalPrice (double)
                );

                db.receiptDao().insertReceipt(newReceipt);

                // Save friends to the database
                for (int i = 0; i < selectedFriends.size(); i++) {
                    String participantName = selectedFriends.get(i);

                    if ("You".equalsIgnoreCase(participantName)) {
                        continue;
                    }

                    Friend groupMember = new Friend(participantName, "", selectedCalendar.getTime());
                    db.friendDao().insertFriend(groupMember);
                }

                runOnUiThread(() -> {
                    Toast.makeText(ReviewSplit.this, "Receipt recorded successfully!", Toast.LENGTH_SHORT).show();
                    Intent intent = new Intent(ReviewSplit.this, SendRequest.class);
                    intent.putExtra("LOGGED_IN_USER_ID", currentUserId);
                    startActivity(intent);
                    finish();
                });

            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> Toast.makeText(ReviewSplit.this, "Database write error.", Toast.LENGTH_SHORT).show());
            }
        });
    }
    private void showDatePickerDialog() {
        int year = selectedCalendar.get(Calendar.YEAR);
        int month = selectedCalendar.get(Calendar.MONTH);
        int day = selectedCalendar.get(Calendar.DAY_OF_MONTH);

        DatePickerDialog datePickerDialog = new DatePickerDialog(
                this,
                (view, selectedYear, selectedMonth, selectedDay) -> {
                    selectedCalendar.set(Calendar.YEAR, selectedYear);
                    selectedCalendar.set(Calendar.MONTH, selectedMonth);
                    selectedCalendar.set(Calendar.DAY_OF_MONTH, selectedDay);

                    java.text.SimpleDateFormat dateFormat = new java.text.SimpleDateFormat("dd MMM yyyy", Locale.getDefault());
                    tvDueDateValue.setText(dateFormat.format(selectedCalendar.getTime()));
                },
                year, month, day
        );

        datePickerDialog.getDatePicker().setMinDate(System.currentTimeMillis() - 1000);
        datePickerDialog.show();
    }

    public static class ReviewAdapter extends RecyclerView.Adapter<ReviewAdapter.ViewHolder> {
        private final List<String> friends;
        private final List<Double> shares;

        public ReviewAdapter(List<String> friends, List<Double> shares) {
            this.friends = friends;
            this.shares = shares;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LinearLayout itemRow = new LinearLayout(parent.getContext());
            itemRow.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(parent.getContext(), 72)));
            itemRow.setOrientation(LinearLayout.HORIZONTAL);
            itemRow.setGravity(Gravity.CENTER_VERTICAL);

            CardView avatarCard = new CardView(parent.getContext());
            LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                    dpToPx(parent.getContext(), 46), dpToPx(parent.getContext(), 46));
            avatarCard.setLayoutParams(cardParams);
            avatarCard.setRadius(dpToPx(parent.getContext(), 23));
            avatarCard.setCardBackgroundColor(0xFFEFEBE4);
            avatarCard.setCardElevation(0);

            TextView tvInitial = new TextView(parent.getContext());
            tvInitial.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            tvInitial.setGravity(Gravity.CENTER);
            tvInitial.setTextColor(0xFF5A189A);
            tvInitial.setTextSize(16);
            tvInitial.setTypeface(null, Typeface.BOLD);
            avatarCard.addView(tvInitial);
            itemRow.addView(avatarCard);

            TextView tvName = new TextView(parent.getContext());
            LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
            nameParams.setMarginStart(dpToPx(parent.getContext(), 16));
            tvName.setLayoutParams(nameParams);
            tvName.setTextColor(0xFF2B2B2B);
            tvName.setTextSize(16);
            tvName.setTypeface(null, Typeface.BOLD);
            itemRow.addView(tvName);

            TextView tvAmount = new TextView(parent.getContext());
            LinearLayout.LayoutParams amountParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            amountParams.setMarginEnd(dpToPx(parent.getContext(), 12));
            tvAmount.setLayoutParams(amountParams);
            tvAmount.setTextColor(0xFF2B2B2B);
            tvAmount.setTextSize(15);
            itemRow.addView(tvAmount);

            ImageView ivEdit = new ImageView(parent.getContext());
            LinearLayout.LayoutParams editParams = new LinearLayout.LayoutParams(
                    dpToPx(parent.getContext(), 20), dpToPx(parent.getContext(), 20));
            ivEdit.setLayoutParams(editParams);
            ivEdit.setImageResource(android.R.drawable.ic_menu_edit);
            ivEdit.setColorFilter(0xFF8A8A8A);
            ivEdit.setClickable(true);
            ivEdit.setFocusable(true);
            itemRow.addView(ivEdit);

            return new ViewHolder(itemRow, tvInitial, tvName, tvAmount, ivEdit);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            String currentName = friends.get(position);
            double currentShare = shares.get(position);

            holder.tvName.setText(currentName);

            if (!currentName.isEmpty()) {
                holder.tvInitial.setText(String.valueOf(currentName.charAt(0)).toUpperCase());
            }

            holder.tvAmount.setText(String.format(Locale.US, "RM %.2f", currentShare));

            holder.ivEdit.setOnClickListener(v -> {
                Context context = v.getContext();
                AlertDialog.Builder builder = new AlertDialog.Builder(context);
                builder.setTitle("Adjust Amount for " + currentName);

                final EditText inputField = new EditText(context);
                inputField.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
                inputField.setText(String.format(Locale.US, "%.2f", currentShare));
                inputField.setSelection(inputField.getText().length());

                LinearLayout container = new LinearLayout(context);
                container.setOrientation(LinearLayout.VERTICAL);
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                params.setMargins(dpToPx(context, 22), dpToPx(context, 10), dpToPx(context, 22), dpToPx(context, 10));
                inputField.setLayoutParams(params);
                container.addView(inputField);
                builder.setView(container);

                builder.setPositiveButton("Update", (dialog, which) -> {
                    String inputVal = inputField.getText().toString().trim();
                    if (!inputVal.isEmpty()) {
                        try {
                            double updatedValue = Double.parseDouble(inputVal);
                            shares.set(position, updatedValue);
                            notifyItemChanged(position);
                        } catch (NumberFormatException e) {
                            Toast.makeText(context, "Invalid amount format", Toast.LENGTH_SHORT).show();
                        }
                    }
                });

                builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());
                builder.show();
            });
        }

        @Override
        public int getItemCount() {
            return friends.size();
        }

        private int dpToPx(android.content.Context context, int dp) {
            return (int) (dp * context.getResources().getDisplayMetrics().density);
        }

        public static class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvInitial;
            TextView tvName;
            TextView tvAmount;
            ImageView ivEdit;

            public ViewHolder(@NonNull View itemView, TextView tvInitial, TextView tvName, TextView tvAmount, ImageView ivEdit) {
                super(itemView);
                this.tvInitial = tvInitial;
                this.tvName = tvName;
                this.tvAmount = tvAmount;
                this.ivEdit = ivEdit;
            }
        }
    }
}