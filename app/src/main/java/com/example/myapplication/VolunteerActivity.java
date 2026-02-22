package com.example.myapplication;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.chip.Chip;
import com.example.myapplication.MainActivity;
import com.example.myapplication.ConnectionHelper;
import com.example.myapplication.databinding.ActivityVolunteerBinding;

import java.util.ArrayList;
import java.util.List;

/**
 * VolunteerActivity — Profile Registration
 *
 * Allows a user to register their name, medical skills, and available equipment.
 * All data is saved to SharedPreferences for offline persistence.
 *
 * IMPORTANT — How this affects the mesh:
 * ─────────────────────────────────────────────────────────────────────────────
 * When the user saves their profile and returns to MainActivity, the
 * ConnectionHelper will rebuild the EndpointName to include their saved name:
 *
 *     "RESQNET|VOLUNTEER|Maria K."
 *
 * Other devices discovering this node will immediately see "VOLUNTEER" and
 * "Maria K." in their discovery results — no connection needed.
 *
 * The full skills/equipment data is stored locally and shared when a full
 * connection is established (as a BYTES payload in the data sync phase).
 */
public class VolunteerActivity extends AppCompatActivity {

    // ── SharedPreferences keys for volunteer profile ──────────────────────────
    public static final String KEY_VOLUNTEER_NAME       = "vol_name";
    public static final String KEY_MEDICAL_SKILLS       = "vol_medical_skills";    // CSV string
    public static final String KEY_EQUIPMENT            = "vol_equipment";          // CSV string
    public static final String KEY_EMERGENCY_CONTACT    = "vol_emergency_contact";
    public static final String KEY_PROFILE_COMPLETE     = "vol_profile_complete";

    private ActivityVolunteerBinding binding;
    private SharedPreferences prefs;

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityVolunteerBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        prefs = getSharedPreferences(MainActivity.PREFS_NAME, MODE_PRIVATE);

        setupToolbar();
        restoreExistingProfile();
        setupSaveButton();
    }

    // ── Toolbar ───────────────────────────────────────────────────────────────

    private void setupToolbar() {
        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Volunteer Registration");
        }
        binding.toolbar.setNavigationOnClickListener(v -> finish());
    }

    // ── Restore saved profile ─────────────────────────────────────────────────

    /**
     * Pre-fills the form with any previously saved data.
     * Since this app works offline, the profile persists across restarts.
     */
    private void restoreExistingProfile() {
        String savedName = prefs.getString(KEY_VOLUNTEER_NAME, "");
        if (!savedName.isEmpty()) {
            binding.etName.setText(savedName);
        }

        String savedContact = prefs.getString(KEY_EMERGENCY_CONTACT, "");
        if (!savedContact.isEmpty()) {
            binding.etEmergencyContact.setText(savedContact);
        }

        // Restore medical skill chip selections
        String savedSkills = prefs.getString(KEY_MEDICAL_SKILLS, "");
        if (!savedSkills.isEmpty()) {
            for (String skill : savedSkills.split(",")) {
                setChipSelected(skill.trim(), true);
            }
        }

        // Restore equipment chip selections
        String savedEquipment = prefs.getString(KEY_EQUIPMENT, "");
        if (!savedEquipment.isEmpty()) {
            for (String eq : savedEquipment.split(",")) {
                setEquipmentChipSelected(eq.trim(), true);
            }
        }

        // Show "profile saved" indicator if this isn't a first-time setup
        if (prefs.getBoolean(KEY_PROFILE_COMPLETE, false)) {
            binding.tvSavedIndicator.setVisibility(View.VISIBLE);
            binding.tvSavedIndicator.setText("✓ Profile saved offline — broadcasting to mesh");
        }
    }

    // ── Save button ───────────────────────────────────────────────────────────

    private void setupSaveButton() {
        binding.btnSaveProfile.setOnClickListener(v -> saveProfile());
    }

    /**
     * Validates the form, saves data to SharedPreferences, and broadcasts to mesh.
     *
     * SharedPreferences is used here (over Room/SQLite) because:
     *   1. The volunteer profile is a single record — key-value pairs are ideal.
     *   2. It's simpler and faster to read, which matters during high-stress situations.
     *   3. It's automatically persisted across app kills and device restarts.
     *
     * The saved name will be picked up by ConnectionHelper.buildEndpointName()
     * the next time the mesh is started or the role is refreshed.
     *
     * NEW: Immediately broadcasts updated profile to all connected peers via JSON
     * so other devices see the changes in real-time (not just on reconnect).
     */
    private void saveProfile() {
        String name = binding.etName.getText() != null
                ? binding.etName.getText().toString().trim() : "";
        String contact = binding.etEmergencyContact.getText() != null
                ? binding.etEmergencyContact.getText().toString().trim() : "";

        // Basic validation
        if (TextUtils.isEmpty(name)) {
            binding.tilName.setError("Please enter your name");
            binding.tilName.requestFocus();
            return;
        }
        binding.tilName.setError(null);

        // Collect selected medical skills from the ChipGroup
        List<String> skills    = getSelectedSkills();
        List<String> equipment = getSelectedEquipment();

        // Save all fields to SharedPreferences (offline, no internet needed)
        prefs.edit()
                .putString(MainActivity.KEY_USER_NAME, name)         // Used in EndpointName
                .putString(KEY_VOLUNTEER_NAME, name)
                .putString(KEY_EMERGENCY_CONTACT, contact)
                .putString(KEY_MEDICAL_SKILLS, String.join(",", skills))
                .putString(KEY_EQUIPMENT, String.join(",", equipment))
                .putBoolean(KEY_PROFILE_COMPLETE, true)
                .apply();

        // NEW: Broadcast updated profile to all connected peers immediately
        // This ensures other devices see changes in real-time
        MeshManager.getInstance().broadcastProfileAsJson(this);

        // Show saved confirmation in the UI
        binding.tvSavedIndicator.setVisibility(View.VISIBLE);
        binding.tvSavedIndicator.setText("✓ Saved offline — your skills will broadcast to nearby devices");

        Toast.makeText(this,
                "Profile saved and broadcasted to team!",
                Toast.LENGTH_LONG).show();

        // Brief delay so the user sees the confirmation, then go back
        binding.btnSaveProfile.postDelayed(this::finish, 1200);
    }

    // ── Chip helpers ──────────────────────────────────────────────────────────

    /** Returns a list of selected medical skill chip labels. */
    private List<String> getSelectedSkills() {
        List<String> selected = new ArrayList<>();
        // Iterate through chip IDs defined in the layout
        int[] skillChipIds = {
                R.id.chipFirstAid, R.id.chipCPR, R.id.chipEMT,
                R.id.chipNurse, R.id.chipDoctor, R.id.chipNone
        };
        for (int id : skillChipIds) {
            Chip chip = binding.chipGroupSkills.findViewById(id);
            if (chip != null && chip.isChecked()) {
                selected.add(chip.getText().toString());
            }
        }
        return selected;
    }

    /** Returns a list of selected equipment chip labels. */
    private List<String> getSelectedEquipment() {
        List<String> selected = new ArrayList<>();
        int[] equipChipIds = {
                R.id.chipFlashlight, R.id.chipRope, R.id.chipVehicle,
                R.id.chipFirstAidKit, R.id.chipRadio, R.id.chipGenerator
        };
        for (int id : equipChipIds) {
            Chip chip = binding.chipGroupEquipment.findViewById(id);
            if (chip != null && chip.isChecked()) {
                selected.add(chip.getText().toString());
            }
        }
        return selected;
    }

    /** Pre-selects a medical skill chip by label (for restoring saved state). */
    private void setChipSelected(String skillLabel, boolean selected) {
        int[] skillChipIds = {
                R.id.chipFirstAid, R.id.chipCPR, R.id.chipEMT,
                R.id.chipNurse, R.id.chipDoctor, R.id.chipNone
        };
        for (int id : skillChipIds) {
            Chip chip = binding.chipGroupSkills.findViewById(id);
            if (chip != null && chip.getText().toString().equalsIgnoreCase(skillLabel)) {
                chip.setChecked(selected);
                break;
            }
        }
    }

    /** Pre-selects an equipment chip by label (for restoring saved state). */
    private void setEquipmentChipSelected(String label, boolean selected) {
        int[] equipChipIds = {
                R.id.chipFlashlight, R.id.chipRope, R.id.chipVehicle,
                R.id.chipFirstAidKit, R.id.chipRadio, R.id.chipGenerator
        };
        for (int id : equipChipIds) {
            Chip chip = binding.chipGroupEquipment.findViewById(id);
            if (chip != null && chip.getText().toString().equalsIgnoreCase(label)) {
                chip.setChecked(selected);
                break;
            }
        }
    }
}