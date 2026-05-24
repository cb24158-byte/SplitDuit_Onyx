package com.example.splitDuit;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SelectFriends extends AppCompatActivity {

    private RecyclerView rvFriendsList;
    private Button btnNext;
    private ImageButton btnBack;
    private EditText etSearchFriends;
    private LinearLayout layoutAddGroup;

    private List<LocalFriend> friendsList;
    private BeautifulFriendsAdapter adapter;

    private AppDatabase db;
    private final ExecutorService databaseExecutor = Executors.newSingleThreadExecutor();

    // Pipeline tracking variables
    private int currentUserId;
    private double totalAmount;
    private String totalAmountStr;
    double extractedTotalNum = 0.0; // Make sure this line exists before line 108!

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_select_friends);

        // 1. Initialize Database Instance
        db = AppDatabase.getInstance(this);

        // 2. Safely capture intent pipeline values passed from previous screen
        currentUserId = getIntent().getIntExtra("LOGGED_IN_USER_ID", 1);
        totalAmount = getIntent().getDoubleExtra("TOTAL_AMOUNT", 0.0);
        totalAmountStr = getIntent().getStringExtra("TOTAL_AMOUNT");

        // 3. Initialize Layout Views
        rvFriendsList = findViewById(R.id.rvFriendsList);
        btnNext = findViewById(R.id.btnNext);
        btnBack = findViewById(R.id.btnBack);
        etSearchFriends = findViewById(R.id.etSearchFriends);
        layoutAddGroup = findViewById(R.id.layoutAddGroup);

        friendsList = new ArrayList<>();

        // 4. Setup RecyclerView Configuration
        rvFriendsList.setLayoutManager(new LinearLayoutManager(this));
        adapter = new BeautifulFriendsAdapter(friendsList, () -> updateButtonCount());
        rvFriendsList.setAdapter(adapter);

        // 5. Connect Real-time Text Filter to the Search Input Box
        setupSearchFilter();

        // 6. Pull existing data rows directly out of local database storage
        loadFriendsFromDatabase();

        btnBack.setOnClickListener(v -> finish());

        layoutAddGroup.setOnClickListener(v -> {
            Intent intent = new Intent(SelectFriends.this, AddFriends.class);
            startActivityForResult(intent, 101);
        });

        btnNext.setOnClickListener(v -> {
            ArrayList<String> selectedNames = new ArrayList<>();
            // Make sure we scan our master source data array for selections
            for (LocalFriend friend : friendsList) {
                if (friend.isSelected) {
                    selectedNames.add(friend.name);
                }
            }

            if (selectedNames.isEmpty()) {
                Toast.makeText(this, "Please select at least one friend", Toast.LENGTH_SHORT).show();
                return;
            }

            // Forward everything safely to the Review split terminal view
            Intent intent = new Intent(SelectFriends.this, ReviewSplit.class);
            intent.putStringArrayListExtra("SELECTED_FRIENDS", selectedNames);
            intent.putExtra("LOGGED_IN_USER_ID", currentUserId);

            if (totalAmountStr != null) {
                intent.putExtra("TOTAL_AMOUNT", totalAmountStr);
            } else {
                intent.putExtra("TOTAL_AMOUNT", totalAmount);
            }

            startActivity(intent);
        });
    }
    private void loadFriendsFromDatabase() {
        databaseExecutor.execute(() -> {
            try {
                List<Friend> savedFriends = db.friendDao().getAllFriends();
                List<LocalFriend> standardUIList = new ArrayList<>();

                // Track unique names using a HashSet
                java.util.Set<String> addedNames = new java.util.HashSet<>();

                for (Friend f : savedFriends) {
                    if (f.getName() != null && !f.getName().trim().isEmpty()) {
                        String name = f.getName().trim();

                        // Convert to lowercase just for the duplicate check to prevent "Ali" and "ali" from both showing up
                        String lowercaseName = name.toLowerCase(Locale.getDefault());

                        // Only add the friend if their name is NOT already in our HashSet
                        if (!addedNames.contains(lowercaseName)) {
                            addedNames.add(lowercaseName); // Mark this name as seen

                            String initial = String.valueOf(name.charAt(0)).toUpperCase();
                            standardUIList.add(new LocalFriend(name, initial));
                        }
                    }
                }

                runOnUiThread(() -> {
                    friendsList.clear();
                    friendsList.addAll(standardUIList);
                    adapter.updateMasterList(friendsList);
                    updateButtonCount();
                });

            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> Toast.makeText(SelectFriends.this, "Failed to load friends.", Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void setupSearchFilter() {
        etSearchFriends.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (adapter != null) {
                    adapter.filter(s.toString());
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == 101 && resultCode == RESULT_OK && data != null) {
            String newFriendName = data.getStringExtra("FRIEND_NAME");

            if (newFriendName != null && !newFriendName.isEmpty()) {
                String initial = String.valueOf(newFriendName.charAt(0)).toUpperCase();
                LocalFriend addedFriend = new LocalFriend(newFriendName, initial);

                // Add to master storage container and sync display changes
                friendsList.add(addedFriend);
                adapter.updateMasterList(friendsList);

                // Automatically auto-check newly added profiles for convenience
                addedFriend.isSelected = true;

                adapter.filter(etSearchFriends.getText().toString());
                updateButtonCount();
            }
        }
    }

    private void updateButtonCount() {
        int count = 0;
        for (LocalFriend friend : friendsList) {
            if (friend.isSelected) {
                count++;
            }
        }
        btnNext.setText("Next (" + count + ")");
    }

    // DATA PRESENTATION MODEL
    public static class LocalFriend {
        String name;
        String initial;
        boolean isSelected;

        public LocalFriend(String name, String initial) {
            this.name = name;
            this.initial = initial;
            this.isSelected = false;
        }
    }

    // ADAPTER LAYER CONTROLLER
    public static class BeautifulFriendsAdapter extends RecyclerView.Adapter<BeautifulFriendsAdapter.ViewHolder> {
        private final List<LocalFriend> masterList;      // Preserves all baseline items and check states
        private final List<LocalFriend> displayedList;   // Changes actively based on filter search terms
        private final Runnable onSelectionChanged;

        public BeautifulFriendsAdapter(List<LocalFriend> list, Runnable onSelectionChanged) {
            this.masterList = new ArrayList<>(list);
            this.displayedList = list;
            this.onSelectionChanged = onSelectionChanged;
        }

        public void updateMasterList(List<LocalFriend> newList) {
            masterList.clear();
            masterList.addAll(newList);
            notifyDataSetChanged();
        }

        /**
         * Runs through active models filtering names matching query rules.
         */
        public void filter(String query) {
            displayedList.clear();
            if (query.isEmpty()) {
                displayedList.addAll(masterList);
            } else {
                String lowerCaseQuery = query.toLowerCase(Locale.getDefault()).trim();
                for (LocalFriend item : masterList) {
                    if (item.name.toLowerCase(Locale.getDefault()).contains(lowerCaseQuery)) {
                        displayedList.add(item);
                    }
                }
            }
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LinearLayout rowContainer = new LinearLayout(parent.getContext());
            rowContainer.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(parent.getContext(), 72)));
            rowContainer.setOrientation(LinearLayout.HORIZONTAL);
            rowContainer.setGravity(Gravity.CENTER_VERTICAL);
            rowContainer.setPadding(0, dpToPx(parent.getContext(), 8), 0, dpToPx(parent.getContext(), 8));

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
            rowContainer.addView(avatarCard);

            TextView tvName = new TextView(parent.getContext());
            LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
            nameParams.setMarginStart(dpToPx(parent.getContext(), 16));
            tvName.setLayoutParams(nameParams);
            tvName.setTextColor(0xFF2B2B2B);
            tvName.setTextSize(16);
            tvName.setTypeface(null, Typeface.BOLD);
            rowContainer.addView(tvName);

            CheckBox checkBox = new CheckBox(parent.getContext());
            LinearLayout.LayoutParams checkParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            checkBox.setLayoutParams(checkParams);
            checkBox.setClickable(false);
            checkBox.setFocusable(false);
            rowContainer.addView(checkBox);

            return new ViewHolder(rowContainer, tvInitial, tvName, checkBox);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            LocalFriend friend = displayedList.get(position);
            holder.tvName.setText(friend.name);
            holder.tvInitial.setText(friend.initial);
            holder.checkBox.setChecked(friend.isSelected);

            holder.itemView.setOnClickListener(v -> {
                friend.isSelected = !friend.isSelected;
                holder.checkBox.setChecked(friend.isSelected);
                onSelectionChanged.run();
            });
        }

        @Override
        public int getItemCount() {
            return displayedList.size();
        }

        private int dpToPx(android.content.Context context, int dp) {
            return (int) (dp * context.getResources().getDisplayMetrics().density);
        }

        public static class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvInitial;
            TextView tvName;
            CheckBox checkBox;

            public ViewHolder(@NonNull View itemView, TextView tvInitial, TextView tvName, CheckBox checkBox) {
                super(itemView);
                this.tvInitial = tvInitial;
                this.tvName = tvName;
                this.checkBox = checkBox;
            }
        }
    }
}