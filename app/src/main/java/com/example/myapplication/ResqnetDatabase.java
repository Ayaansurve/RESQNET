package com.example.myapplication;

import android.util.Log;

import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ResqnetDatabase {

    private static final String TAG = "RESQNET_DB";
    private static final String COLLECTION = "resqnet_meshes";

    private static ResqnetDatabase instance;
    private final FirebaseFirestore db;

    private ResqnetDatabase() {
        db = FirebaseFirestore.getInstance();
    }

    public static synchronized ResqnetDatabase getInstance() {
        if (instance == null)
            instance = new ResqnetDatabase();
        return instance;
    }

    // =====================================================
    // 1️⃣ CREATE OR UPDATE MESH DOCUMENT
    // =====================================================

    public void createOrUpdateMesh(String meshId,
                                   String areaName,
                                   int deviceCount,
                                   String bridgeDeviceId) {

        Map<String, Object> mesh = new HashMap<>();
        mesh.put("mesh_id", meshId);
        mesh.put("area_name", areaName);
        mesh.put("timestamp", FieldValue.serverTimestamp());
        mesh.put("mesh_device_count", deviceCount);
        mesh.put("bridge_device_id", bridgeDeviceId);

        db.collection(COLLECTION)
                .document(meshId)
                .set(mesh, SetOptions.merge())
                .addOnSuccessListener(unused ->
                        Log.d(TAG, "Mesh created/updated"))
                .addOnFailureListener(e ->
                        Log.e(TAG, "Mesh error", e));
    }

    // =====================================================
    // 2️⃣ ADD SURVIVOR TO MESH
    // =====================================================

    public void addSurvivor(String meshId,
                            String deviceId,
                            String address,
                            int noOfPeople,
                            int age,
                            String injuryLevel,
                            String customMessage) {

        Map<String, Object> survivor = new HashMap<>();
        survivor.put("device_id", deviceId);
        survivor.put("address", address);
        survivor.put("no_of_people", noOfPeople);
        survivor.put("age", age);
        survivor.put("injury_level", injuryLevel);
        survivor.put("custom_message", customMessage);
        survivor.put("is_responded", false);
        survivor.put("is_sent_online", false);

        db.collection(COLLECTION)
                .document(meshId)
                .update("survivors", FieldValue.arrayUnion(survivor))
                .addOnSuccessListener(unused ->
                        Log.d(TAG, "Survivor added"))
                .addOnFailureListener(e ->
                        Log.e(TAG, "Add survivor failed", e));
    }

    // =====================================================
    // 3️⃣ MARK SURVIVOR RESPONDED
    // =====================================================

    public void markSurvivorResponded(String meshId,
                                      String deviceId) {

        db.collection(COLLECTION)
                .document(meshId)
                .get()
                .addOnSuccessListener(snapshot -> {

                    if (!snapshot.exists()) return;

                    List<Map<String, Object>> survivors =
                            (List<Map<String, Object>>) snapshot.get("survivors");

                    if (survivors == null) return;

                    for (Map<String, Object> s : survivors) {
                        if (deviceId.equals(s.get("device_id"))) {
                            s.put("is_responded", true);
                        }
                    }

                    db.collection(COLLECTION)
                            .document(meshId)
                            .update("survivors", survivors);
                });
    }

    // =====================================================
    // 4️⃣ FETCH FULL MESH DATA
    // =====================================================

    public void fetchMesh(String meshId,
                          MeshCallback callback) {

        db.collection(COLLECTION)
                .document(meshId)
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (snapshot.exists()) {
                        callback.onSuccess(snapshot.getData());
                    } else {
                        callback.onFailure("Mesh not found");
                    }
                })
                .addOnFailureListener(e ->
                        callback.onFailure(e.getMessage()));
    }

    // =====================================================
    // CALLBACK INTERFACE
    // =====================================================

    public interface MeshCallback {
        void onSuccess(Map<String, Object> data);
        void onFailure(String error);
    }
}