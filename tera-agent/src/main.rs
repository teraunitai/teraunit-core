use std::{thread, time, process};
use std::process::Command;
use sysinfo::{System, SystemExt, DiskExt};
use std::time::{Instant, Duration};

// CONFIGURATION
// Prefer ENV to avoid baking control-plane URLs into binaries.
const DEFAULT_SERVER_URL: &str = "https://teraunit-core.onrender.com/v1/heartbeat";
const DEATH_TIMEOUT: u64 = 300; // 5 Minutes

fn main() {
    println!("[TERA-AGENT] 🚀 TITANIUM PROTOCOL INITIATED...");

    let server_url = std::env::var("TERA_HEARTBEAT_URL").unwrap_or_else(|_| DEFAULT_SERVER_URL.to_string());
    let heartbeat_id_env = std::env::var("TERA_HEARTBEAT_ID").ok();
    let heartbeat_token_env = std::env::var("TERA_HEARTBEAT_TOKEN").ok();

    // 1. IDENTITY LOCK
    let machine_id = hostname::get()
        .unwrap_or_else(|_| "UNKNOWN_UNIT".into())
        .into_string()
        .unwrap_or("UNKNOWN".into());

    // Control plane binds heartbeat to a stable id; fall back to hostname if not provided.
    let heartbeat_id = heartbeat_id_env.unwrap_or_else(|| machine_id.clone());

    println!("[TERA-AGENT] 🔒 ID LOCKED: {}", heartbeat_id);

    // 2. HARDWARE SCAN (Data Warmer Check)
    let mut sys = System::new_all();
    sys.refresh_disks();

    // Look for high-speed local storage (NVMe or Ephemeral)
    let has_nvme = sys.disks().iter().any(|disk| {
        let name = disk.name().to_string_lossy().to_lowercase();
        name.contains("nvme") || name.contains("ephemeral") || name.contains("local")
    });

    if has_nvme {
        println!("[TERA-AGENT] ⚡ HIGH-SPEED NVME DETECTED. DATA WARMER READY.");
    } else {
        println!("[TERA-AGENT] ⚠️ NO NVME FOUND. RUNNING IN LEGACY MODE.");
    }

    let client = reqwest::blocking::Client::new();
    let mut last_successful_pulse = Instant::now();

    // Safety: avoid accidentally shutting down a developer machine.
    // Set TERA_KILL_SWITCH_ARMED=true on real instances.
    let kill_switch_armed = std::env::var("TERA_KILL_SWITCH_ARMED")
        .map(|v| matches!(v.as_str(), "1" | "true" | "TRUE" | "yes" | "YES"))
        .unwrap_or(false);

    // 3. THE IMMORTAL LOOP
    loop {
        sys.refresh_memory();

        // Construct Payload
        let payload = serde_json::json!({
            "id": heartbeat_id,
            "status": "alive",
            "ram_used": sys.used_memory(),
            "nvme_ready": has_nvme
        });

        println!("[TERA-AGENT] 💓 PULSING SERVER...");

        let mut req = client.post(&server_url).json(&payload);
        if let Some(token) = heartbeat_token_env.as_ref() {
            req = req.header("X-Tera-Heartbeat-Token", token);
        }

        match req.send() {
            Ok(resp) => {
                if resp.status().is_success() {
                    println!("[TERA-AGENT] ✅ ACKNOWLEDGED.");
                    last_successful_pulse = Instant::now(); // Reset Death Timer
                } else {
                    eprintln!("[TERA-AGENT] ⚠️ SERVER REJECTED: {}", resp.status());
                }
            }
            Err(e) => {
                eprintln!("[TERA-AGENT] ❌ NETWORK FAILURE: {}", e);
            }
        }

        // 4. THE ZOMBIE KILL SWITCH (Active Defense)
        let time_since_contact = last_successful_pulse.elapsed().as_secs();
        if time_since_contact > DEATH_TIMEOUT {
            println!("[TERA-AGENT] 💀 CONNECTION LOST FOR {}s. EXECUTING KILL SWITCH.", time_since_contact);
            println!("[TERA-AGENT] 💸 SAVING USER WALLET. SHUTTING DOWN NOW.");

            if kill_switch_armed {
                let _ = Command::new("shutdown").arg("-h").arg("now").spawn();
                let _ = Command::new("systemctl").arg("poweroff").arg("-i").spawn();
                let _ = Command::new("poweroff").arg("-f").spawn();
                let _ = Command::new("halt").arg("-f").spawn();
            }

            // Always exit so the container/agent stops even if shutdown isn't permitted.
            process::exit(42);
        }

        // Sleep 60s
        thread::sleep(time::Duration::from_secs(60));
    }
}
