package com.example.splitDuit;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Base64;
import android.util.Size;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ScanReceipt extends AppCompatActivity {

    PreviewView previewView;
    Button btnCapture;
    TableLayout tableReceipt;
    TextView txtTotalAmount;
    ImageCapture imageCapture;
    ImageButton btnBack;

    private AppDatabase db;
    private int currentUserId;
    private double extractedTotalNum = 0.00;
    private String extractedTotalStr = "RM 0.00";
    private final ExecutorService backgroundExecutor = Executors.newSingleThreadExecutor();

    // Unique session ID tracking field to logically group separate items as one single receipt
    private String receiptSessionToken;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scan_receipt);

        db = AppDatabase.getInstance(this);

        // Fetch logged-in account (Defaults safely to 1 if missing)
        currentUserId = getIntent().getIntExtra("LOGGED_IN_USER_ID", 1);

        // Generate a distinct numerical timestamp hash string code to act as our tracking handle
        receiptSessionToken = "BILL_" + (System.currentTimeMillis() / 1000);

        btnBack = findViewById(R.id.btnBack);
        btnBack.setOnClickListener(v -> {
            Intent intent = new Intent(ScanReceipt.this, MainActivity.class);
            intent.putExtra("LOGGED_IN_USER_ID", currentUserId);
            startActivity(intent);
            finish();
        });

        previewView = findViewById(R.id.previewView);
        btnCapture = findViewById(R.id.btnCapture);
        tableReceipt = findViewById(R.id.tableReceipt);
        txtTotalAmount = findViewById(R.id.txtTotalAmount);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, 101);
        } else {
            startCamera();
        }

        btnCapture.setOnClickListener(v -> {
            if (btnCapture.getText().toString().equals("Proceed to Split")) {
                saveReceiptToDatabase();
            } else {
                captureImage();
            }
        });
    }

    private void saveReceiptToDatabase() {
        btnCapture.setEnabled(false);
        btnCapture.setText("Saving Receipt...");

        backgroundExecutor.execute(() -> {
            try {
                boolean hasSavedItems = false;

                // Loop through UI Table rows (skip header row at index 0)
                for (int i = 1; i < tableReceipt.getChildCount(); i++) {
                    TableRow row = (TableRow) tableReceipt.getChildAt(i);
                    TextView txtName = (TextView) row.getChildAt(0);
                    TextView txtPrice = (TextView) row.getChildAt(1);

                    String itemNameStr = txtName.getText().toString();
                    String priceStr = txtPrice.getText().toString().replace("RM", "").trim();

                    try {
                        double parsedPrice = Double.parseDouble(priceStr);

                        // Match entity constructor exactly: Receipt(userId, shopName, item, price, totalPrice)
                        // 🔥 We store our unique session token into "shopName" to bundle items together
                        Receipt singleItemReceipt = new Receipt(
                                currentUserId,
                                receiptSessionToken,
                                itemNameStr,
                                parsedPrice,
                                extractedTotalNum
                        );

                        db.receiptDao().insertReceipt(singleItemReceipt);
                        hasSavedItems = true;

                    } catch (NumberFormatException ignored) {
                        // Skip tax headers or unparseable lines smoothly
                    }
                }

                // Fallback: If no custom rows were processed, write a master summary tracking record
                if (!hasSavedItems) {
                    Receipt masterReceipt = new Receipt(
                            currentUserId,
                            receiptSessionToken,
                            "Total Summary Receipt",
                            extractedTotalNum,
                            extractedTotalNum
                    );
                    db.receiptDao().insertReceipt(masterReceipt);
                }

                runOnUiThread(() -> {
                    Toast.makeText(ScanReceipt.this, "Receipt items saved successfully!", Toast.LENGTH_SHORT).show();

                    Intent intent = new Intent(ScanReceipt.this, SplitMethod.class);
                    intent.putExtra("LOGGED_IN_USER_ID", currentUserId);

                    // Pass the hashcode of our token string as an int so it passes cleanly to downstream screens
                    intent.putExtra("SAVED_RECEIPT_ID", receiptSessionToken.hashCode());
                    intent.putExtra("TOTAL_AMOUNT", String.format(Locale.US, "RM %.2f", extractedTotalNum));

                    startActivity(intent);
                    finish();
                });

            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    Toast.makeText(ScanReceipt.this, "Database storage failure.", Toast.LENGTH_SHORT).show();
                    btnCapture.setEnabled(true);
                    btnCapture.setText("Proceed to Split");
                });
            }
        });
    }

    private void startCamera() {
        com.google.common.util.concurrent.ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                imageCapture = new ImageCapture.Builder()
                        .setTargetResolution(new Size(1280, 720))
                        .build();

                CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;
                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void captureImage() {
        File file = new File(getExternalCacheDir(), "receipt.jpg");
        ImageCapture.OutputFileOptions outputOptions = new ImageCapture.OutputFileOptions.Builder(file).build();

        btnCapture.setEnabled(false);
        btnCapture.setText("Capturing Image...");

        imageCapture.takePicture(
                outputOptions,
                ContextCompat.getMainExecutor(this),
                new ImageCapture.OnImageSavedCallback() {
                    @Override
                    public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                        btnCapture.setText("AI Extracting Details...");
                        processImageWithVisionAI(file);
                    }

                    @Override
                    public void onError(@NonNull ImageCaptureException exception) {
                        resetCaptureButton();
                        exception.printStackTrace();
                        Toast.makeText(ScanReceipt.this, "Failed to capture picture", Toast.LENGTH_SHORT).show();
                    }
                }
        );
    }

    private void processImageWithVisionAI(File imageFile) {
        String myGeminiKey = "AIzaSyClEBwDVlB4ohcqRuBC1-3sL_sX6lgVGdg";

        backgroundExecutor.execute(() -> {
            try {
                Bitmap bitmap = BitmapFactory.decodeFile(imageFile.getAbsolutePath());
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                bitmap.compress(Bitmap.CompressFormat.JPEG, 50, outputStream);
                byte[] byteArray = outputStream.toByteArray();
                String base64Image = Base64.encodeToString(byteArray, Base64.NO_WRAP);

                String promptText = "Examine this Malaysian dining receipt image visually. " +
                        "Extract ONLY itemized foods, individual prices, tax, and grand total. " +
                        "Return response strictly matching this JSON schema: " +
                        "{\"items\": [{\"name\": \"Item\", \"price\": 0.00}], \"tax\": 0.00, \"total\": 0.00}";

                JSONObject textPart = new JSONObject().put("text", promptText);
                JSONObject inlineData = new JSONObject()
                        .put("mimeType", "image/jpeg")
                        .put("data", base64Image);
                JSONObject imagePart = new JSONObject().put("inlineData", inlineData);

                JSONArray partsArray = new JSONArray();
                partsArray.put(textPart);
                partsArray.put(imagePart);

                JSONObject contentObj = new JSONObject().put("parts", partsArray);
                JSONArray contentsArray = new JSONArray().put(contentObj);
                JSONObject responseConfig = new JSONObject().put("responseMimeType", "application/json");

                JSONObject requestBody = new JSONObject()
                        .put("contents", contentsArray)
                        .put("generationConfig", responseConfig);

                URL url = new URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=" + myGeminiKey);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                conn.setDoOutput(true);

                try (OutputStream os = conn.getOutputStream()) {
                    byte[] input = requestBody.toString().getBytes(StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                }

                int responseCode = conn.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    InputStream is = conn.getInputStream();
                    java.util.Scanner scanner = new java.util.Scanner(is, "UTF-8").useDelimiter("\\A");
                    String responseStr = scanner.hasNext() ? scanner.next() : "";

                    JSONObject responseJson = new JSONObject(responseStr);
                    String rawTextResult = responseJson.getJSONArray("candidates")
                            .getJSONObject(0)
                            .getJSONObject("content")
                            .getJSONArray("parts")
                            .getJSONObject(0)
                            .getString("text");

                    runOnUiThread(() -> updateReceiptUI(rawTextResult.trim()));
                } else {
                    runOnUiThread(() -> {
                        Toast.makeText(ScanReceipt.this, "Server API Issue Code: " + responseCode, Toast.LENGTH_LONG).show();
                        resetCaptureButton();
                    });
                }
                conn.disconnect();

            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    Toast.makeText(ScanReceipt.this, "Network connection error.", Toast.LENGTH_LONG).show();
                    resetCaptureButton();
                });
            }
        });
    }

    private void updateReceiptUI(String jsonString) {
        try {
            int childCount = tableReceipt.getChildCount();
            if (childCount > 1) {
                tableReceipt.removeViews(1, childCount - 1);
            }
            txtTotalAmount.setText("RM 0.00");

            if (jsonString.startsWith("```json")) {
                jsonString = jsonString.substring(7, jsonString.length() - 3).trim();
            } else if (jsonString.startsWith("```")) {
                jsonString = jsonString.substring(3, jsonString.length() - 3).trim();
            }

            JSONObject data = new JSONObject(jsonString);

            if (!data.has("items")) {
                Toast.makeText(this, "No items found. Please re-scan.", Toast.LENGTH_LONG).show();
                resetCaptureButton();
                return;
            }

            JSONArray items = data.getJSONArray("items");
            for (int i = 0; i < items.length(); i++) {
                JSONObject itemObj = items.getJSONObject(i);
                String name = itemObj.optString("name", "");
                double price = itemObj.optDouble("price", 0.0);

                if (name.isEmpty() || price <= 0) continue;

                addTableRow(name, String.format(Locale.US, "%.2f", price));
            }

            double tax = data.optDouble("tax", 0.0);
            if (tax > 0) {
                addTableRow("Tax / SST", String.format(Locale.US, "%.2f", tax));
            }

            extractedTotalNum = data.optDouble("total", 0.0);
            extractedTotalStr = "RM " + String.format(Locale.US, "%.2f", extractedTotalNum);
            txtTotalAmount.setText(extractedTotalStr);

            btnCapture.setEnabled(true);
            btnCapture.setText("Proceed to Split");

        } catch (JSONException e) {
            e.printStackTrace();
            Toast.makeText(this, "Failed to parse receipt layout.", Toast.LENGTH_LONG).show();
            resetCaptureButton();
        }
    }

    private void addTableRow(String itemText, String priceText) {
        TableRow row = new TableRow(this);
        TextView item = new TextView(this);
        TextView price = new TextView(this);

        item.setText(itemText);
        price.setText("RM " + priceText);
        item.setTextSize(16);
        price.setTextSize(16);
        item.setPadding(12, 12, 12, 12);
        price.setPadding(12, 12, 12, 12);

        row.addView(item);
        row.addView(price);
        tableReceipt.addView(row);
    }

    private void resetCaptureButton() {
        btnCapture.setEnabled(true);
        btnCapture.setText("Scan Receipt");
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 101 && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        }
    }
}