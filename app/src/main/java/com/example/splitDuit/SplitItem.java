package com.example.splitDuit;

import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SplitItem extends AppCompatActivity {

    private static final String TAG = "SplitItemActivity";

    private RecyclerView rvItemsFromBill, rvHorizontalPeopleCarousel;
    private TextView tvHeadcountSummary, lblAddPersonButton;
    private Button btnReviewSummary;
    private ImageButton btnBackSplitItem;

    // UI components for the summary card
    private TextView tvTotalAmount, tvSubtotal, tvTax, tvItemsTitleLabel;

    private List<BillItemObject> billItemList;
    private List<String> workspacePeopleNames;

    private BillItemRowAdapter itemsAdapter;
    private BottomCarouselAdapter peopleAdapter;

    private AppDatabase db;
    private final ExecutorService databaseExecutor = Executors.newSingleThreadExecutor();
    private int currentUserId;
    private int savedReceiptId; // Acts as the session/group ID in this context

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_split_item);

        // Initialize Database
        db = AppDatabase.getInstance(this);

        // Get Intent Extras (Defaults provided to prevent crashes if missing)
        currentUserId = getIntent().getIntExtra("LOGGED_IN_USER_ID", 1);
        savedReceiptId = getIntent().getIntExtra("SAVED_RECEIPT_ID", -1);

        Log.d(TAG, "Loaded Activity with UserID: " + currentUserId + " and ReceiptID: " + savedReceiptId);

        // 1. Bind UI Components
        rvItemsFromBill = findViewById(R.id.rvItemsFromBill);
        rvHorizontalPeopleCarousel = findViewById(R.id.rvHorizontalPeopleCarousel);
        tvHeadcountSummary = findViewById(R.id.tvHeadcountSummary);
        lblAddPersonButton = findViewById(R.id.lblAddPersonButton);
        btnReviewSummary = findViewById(R.id.btnReviewSummary);
        btnBackSplitItem = findViewById(R.id.btnBackSplitItem);

        tvTotalAmount = findViewById(R.id.totalAmount);
        tvSubtotal = findViewById(R.id.tvSubtotal);
        tvTax = findViewById(R.id.tvTax);
        tvItemsTitleLabel = findViewById(R.id.tvItemsTitleLabel);

        // 2. Initialize Lists FIRST
        billItemList = new ArrayList<>();
        workspacePeopleNames = new ArrayList<>();
        workspacePeopleNames.add("You"); // Default user

        // 3. Setup Layout Managers & Adapters
        rvItemsFromBill.setLayoutManager(new LinearLayoutManager(this));
        itemsAdapter = new BillItemRowAdapter(billItemList, this::showAssigneePickerDialog);
        rvItemsFromBill.setAdapter(itemsAdapter);

        LinearLayoutManager horizontalLayout = new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false);
        rvHorizontalPeopleCarousel.setLayoutManager(horizontalLayout);
        peopleAdapter = new BottomCarouselAdapter(workspacePeopleNames);
        rvHorizontalPeopleCarousel.setAdapter(peopleAdapter);

        updateHeadcountSummaryText();

        // 4. Load Data From Receipt Database
        loadReceiptData();

        // 5. Click Listeners
        btnBackSplitItem.setOnClickListener(v -> finish());

        lblAddPersonButton.setOnClickListener(v -> {
            Intent intent = new Intent(SplitItem.this, AddFriends.class);
            startActivityForResult(intent, 101);
        });

        btnReviewSummary.setOnClickListener(v -> {
            if (billItemList.isEmpty()) {
                Toast.makeText(this, "Please add items to split first!", Toast.LENGTH_SHORT).show();
                return;
            }

            Intent intent = new Intent(SplitItem.this, SplitByItemReview.class);
            intent.putExtra("BILL_ITEMS_DATA", new ArrayList<>(billItemList));
            startActivity(intent);
        });
    }

    private void loadReceiptData() {
        if (savedReceiptId == -1) {
            Log.e(TAG, "Invalid Receipt ID. Aborting database fetch.");
            Toast.makeText(this, "Error: Could not trace receipt records.", Toast.LENGTH_LONG).show();
            return;
        }

        databaseExecutor.execute(() -> {
            try {
                // 1. Fetch ALL receipt records matching the logged-in user
                List<Receipt> allUserItems = db.receiptDao().getAllReceiptsForUser(currentUserId);

                runOnUiThread(() -> {
                    billItemList.clear();
                    double subtotal = 0.0;

                    if (allUserItems != null) {
                        for (Receipt itemRecord : allUserItems) {
                            if (itemRecord.shopName != null) {

                                // 🔥 SMART DOUBLE-CHECK: Matches String format or Hashcode format perfectly
                                boolean matchesManual = itemRecord.shopName.equals(String.valueOf(savedReceiptId));
                                boolean matchesScanHash = (itemRecord.shopName.hashCode() == savedReceiptId);

                                if (matchesManual || matchesScanHash) {
                                    String name = itemRecord.getItem();
                                    double price = itemRecord.getPrice();

                                    subtotal += price;

                                    // Add to the RecyclerView structural data array
                                    billItemList.add(new BillItemObject(name, price, "You"));
                                }
                            }
                        }
                    }

                    // Calculate tax and total (6% tax standard conversion)
                    double tax = subtotal * 0.06;
                    double grandTotal = subtotal + tax;

                    // Update UI text fields
                    tvSubtotal.setText(String.format(Locale.US, "RM %.2f", subtotal));
                    tvTax.setText(String.format(Locale.US, "RM %.2f", tax));
                    tvTotalAmount.setText(String.format(Locale.US, "RM %.2f", grandTotal));
                    tvItemsTitleLabel.setText("Items from Bill (" + billItemList.size() + ")");

                    // Refresh the adapter components safely
                    itemsAdapter.notifyDataSetChanged();
                    Log.d(TAG, "Successfully populated UI with " + billItemList.size() + " items.");
                });

            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    Toast.makeText(SplitItem.this, "Error processing list data.", Toast.LENGTH_SHORT).show();
                });
            }
        });
    }
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 101 && resultCode == RESULT_OK && data != null) {
            String newFriendName = data.getStringExtra("FRIEND_NAME");
            if (newFriendName != null && !newFriendName.isEmpty()) {
                workspacePeopleNames.add(newFriendName);
                peopleAdapter.notifyItemInserted(workspacePeopleNames.size() - 1);
                updateHeadcountSummaryText();
            }
        }
    }

    private void updateHeadcountSummaryText() {
        tvHeadcountSummary.setText("People in this Split (" + workspacePeopleNames.size() + ")");
    }

    private void showAssigneePickerDialog(int itemIndexPosition) {
        String[] options = workspacePeopleNames.toArray(new String[0]);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Assign \"" + billItemList.get(itemIndexPosition).itemName + "\" to:");
        builder.setItems(options, (dialog, chosenIndex) -> {
            billItemList.get(itemIndexPosition).assignedPersonName = options[chosenIndex];
            itemsAdapter.notifyItemChanged(itemIndexPosition);
        });
        builder.show();
    }

    // --- DATA ELEMENT OBJECT ---
    public static class BillItemObject implements java.io.Serializable {
        private static final long serialVersionUID = 1L;

        public String itemName;
        public double price;
        public String assignedPersonName;

        public BillItemObject(String itemName, double price, String assignedPersonName) {
            this.itemName = itemName;
            this.price = price;
            this.assignedPersonName = assignedPersonName;
        }
    }

    // --- ADAPTER 1: VERTICAL ITEMS LIST ---
    public static class BillItemRowAdapter extends RecyclerView.Adapter<BillItemRowAdapter.ViewHolder> {
        private final List<BillItemObject> itemList;
        private final OnDropdownClickListener clickListener;

        public interface OnDropdownClickListener { void onDropdownClicked(int position); }

        public BillItemRowAdapter(List<BillItemObject> itemList, OnDropdownClickListener clickListener) {
            this.itemList = itemList;
            this.clickListener = clickListener;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            Context context = parent.getContext();
            CardView containerCard = new CardView(context);
            LinearLayout.LayoutParams containerParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            containerParams.setMargins(0, dpToPx(context, 4), 0, dpToPx(context, 6));
            containerCard.setLayoutParams(containerParams);
            containerCard.setRadius(dpToPx(context, 12));
            containerCard.setCardBackgroundColor(0xFFFFFFFF);
            containerCard.setCardElevation(dpToPx(context, 1));

            LinearLayout row = new LinearLayout(context);
            row.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(context, 72)));
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dpToPx(context, 14), 0, dpToPx(context, 14), 0);

            LinearLayout textContainer = new LinearLayout(context);
            textContainer.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams tParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
            textContainer.setLayoutParams(tParams);

            TextView tvName = new TextView(context);
            tvName.setTextColor(0xFF1C1B1F);
            tvName.setTextSize(16);
            tvName.setTypeface(null, Typeface.BOLD);
            textContainer.addView(tvName);

            TextView tvPrice = new TextView(context);
            tvPrice.setTextColor(0xFF7A757F);
            tvPrice.setTextSize(14);
            textContainer.addView(tvPrice);
            row.addView(textContainer);

            TextView tvDropdownChip = new TextView(context);
            LinearLayout.LayoutParams dropParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            tvDropdownChip.setLayoutParams(dropParams);
            tvDropdownChip.setPadding(dpToPx(context, 16), dpToPx(context, 8), dpToPx(context, 16), dpToPx(context, 8));
            tvDropdownChip.setBackgroundColor(0xFFF0ECE4);
            tvDropdownChip.setTextColor(0xFF5A189A);
            tvDropdownChip.setTypeface(null, Typeface.BOLD);
            tvDropdownChip.setTextSize(14);
            row.addView(tvDropdownChip);

            containerCard.addView(row);
            return new ViewHolder(containerCard, tvName, tvPrice, tvDropdownChip);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            BillItemObject item = itemList.get(position);
            holder.tvName.setText(item.itemName);
            holder.tvPrice.setText(String.format(Locale.US, "RM %.2f", item.price));
            holder.tvDropdownChip.setText(item.assignedPersonName + " ▾");
            holder.tvDropdownChip.setOnClickListener(v -> clickListener.onDropdownClicked(position));
        }

        @Override
        public int getItemCount() { return itemList.size(); }
        private int dpToPx(Context c, int dp) { return (int) (dp * c.getResources().getDisplayMetrics().density); }

        public static class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvName, tvPrice, tvDropdownChip;
            public ViewHolder(@NonNull View itemView, TextView tvName, TextView tvPrice, TextView tvDropdownChip) {
                super(itemView);
                this.tvName = tvName;
                this.tvPrice = tvPrice;
                this.tvDropdownChip = tvDropdownChip;
            }
        }
    }

    // --- ADAPTER 2: BOTTOM HORIZONTAL PROFILE TRAY ---
    public static class BottomCarouselAdapter extends RecyclerView.Adapter<BottomCarouselAdapter.ViewHolder> {
        private final List<String> itemsList;
        public BottomCarouselAdapter(List<String> itemsList) { this.itemsList = itemsList; }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            Context context = parent.getContext();
            LinearLayout cellLayoutContainer = new LinearLayout(context);
            cellLayoutContainer.setLayoutParams(new ViewGroup.LayoutParams(dpToPx(context, 64), ViewGroup.LayoutParams.MATCH_PARENT));
            cellLayoutContainer.setOrientation(LinearLayout.VERTICAL);
            cellLayoutContainer.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.CENTER_VERTICAL);

            CardView avatarCircleCard = new CardView(context);
            LinearLayout.LayoutParams circleParams = new LinearLayout.LayoutParams(dpToPx(context, 44), dpToPx(context, 44));
            circleParams.bottomMargin = dpToPx(context, 4);
            avatarCircleCard.setLayoutParams(circleParams);
            avatarCircleCard.setRadius(dpToPx(context, 22));
            avatarCircleCard.setCardBackgroundColor(0xFFEFEBE4);
            avatarCircleCard.setCardElevation(0);

            TextView tvBadgeText = new TextView(context);
            tvBadgeText.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            tvBadgeText.setGravity(Gravity.CENTER);
            tvBadgeText.setTextColor(0xFF5A189A);
            tvBadgeText.setTextSize(15);
            tvBadgeText.setTypeface(null, Typeface.BOLD);
            avatarCircleCard.addView(tvBadgeText);
            cellLayoutContainer.addView(avatarCircleCard);

            TextView tvProfileLabelName = new TextView(context);
            tvProfileLabelName.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            tvProfileLabelName.setTextSize(11);
            tvProfileLabelName.setTextColor(0xFF1C1B1F);
            cellLayoutContainer.addView(tvProfileLabelName);

            return new ViewHolder(cellLayoutContainer, tvBadgeText, tvProfileLabelName);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            String profileNodeName = itemsList.get(position);
            holder.tvProfileLabelName.setText(profileNodeName);
            if (profileNodeName != null && !profileNodeName.isEmpty()) {
                holder.tvBadgeText.setText(String.valueOf(profileNodeName.charAt(0)).toUpperCase());
            }
        }

        @Override
        public int getItemCount() { return itemsList.size(); }
        private int dpToPx(Context c, int dp) { return (int) (dp * c.getResources().getDisplayMetrics().density); }

        public static class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvBadgeText, tvProfileLabelName;
            public ViewHolder(@NonNull View itemView, TextView tvBadgeText, TextView tvProfileLabelName) {
                super(itemView);
                this.tvBadgeText = tvBadgeText;
                this.tvProfileLabelName = tvProfileLabelName;
            }
        }
    }
}