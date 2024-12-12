package stucoplugin;

import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.advancement.Advancement;
import org.bukkit.advancement.AdvancementProgress;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;

public class StudentUI {
  public static VirtualUI getAdvancementUI(Player sender, Advancement adv, int assignmentIndex, String hwName)
      throws SQLException {
    DBConnect.Query q;
    DBConnect db = Main.db;
    String term = Main.term;

    // List all student andrewID(varchar(200)) and uuid(varchar(200))
    VirtualUI ui = new VirtualUI("Advancement for " + hwName, 54);
    q = db.queryDB("SELECT andrewID, uuid FROM intro2mc_student;");
    ArrayList<String> andrewIDs = new ArrayList<String>();
    ArrayList<Boolean> completeds = new ArrayList<Boolean>();
    while (q.rs.next()) {
      String andrewID = q.rs.getString("andrewID");
      String uuid = q.rs.getString("uuid");
      andrewIDs.add(andrewID);

      // setting items in virtual inventory
      OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(UUID.fromString(uuid));
      Player player = offlinePlayer.getPlayer(); // Load the player's profile from disk
      if (player == null) {
        ui.addItemStack(-1, offlinePlayer.getUniqueId(), andrewID, Arrays.asList("Player Offline"), false, null, null, null, null);
        completeds.add(false);
      } else {
        AdvancementProgress progress = player.getAdvancementProgress(adv);

        Boolean completed = progress.isDone();
        completeds.add(completed);
        ArrayList<String> lore = new ArrayList<String>();
        lore.add(adv.toString() + " " + (completed ? "completed" : "not completed"));
        if (progress.getAwardedCriteria().size() == 0) {
          lore.add("Awarded Criteria: (none)");
        } else {
          lore.add("Awarded Criteria:");
          for (String criteria : progress.getAwardedCriteria()) {
            lore.add(" - " + criteria);
          }
        }
        if (progress.getRemainingCriteria().size() == 0) {
          lore.add("Remaining Criteria: (none)");
        } else {
          lore.add("Remaining Criteria:");
          for (String criteria : progress.getRemainingCriteria()) {
            lore.add(" - " + criteria);
          }
        }
        ui.addItemStack(-1, player.getUniqueId(), andrewID, lore, completed, null, null, null, null);
      }
    }
    q.close();

    VirtualUICallback callback = (VirtualUI callbackUI, Player player, ItemStack item, int slot, int index) -> {
      Main.updateAdvancement(player, assignmentIndex, adv);
    };

    ui.addLineBreak(1, Material.BLACK_STAINED_GLASS_PANE);
    VirtualUICallback refreshCallback = VirtualUICallback.withConfirm("Refresh Advancement HW?", null, callback);
    ui.addItemStack(-1, Material.GREEN_CONCRETE, "Confirm",
        Arrays.asList("update " + adv.toString() + " to " + hwName), false,
        refreshCallback, refreshCallback, refreshCallback, refreshCallback);
    return ui;
  }

  public static VirtualUI getRosterUI() throws SQLException {
    DBConnect.Query q;
    DBConnect db = Main.db;
    String term = Main.term;

    // List all student andrewID(varchar(200)) and uuid(varchar(200))
    q = db.queryDB("SELECT andrewID, name, uuid, discord FROM intro2mc_student;");
    VirtualUI ui = new VirtualUI("Student Roster (" + term + ")", 54);
    while (q.rs.next()) {
      String andrewID = q.rs.getString("andrewID");
      String name = q.rs.getString("name");
      String uuid = q.rs.getString("uuid");
      String discord = q.rs.getString("discord");
      UUID uu = UUID.fromString(uuid);
      Boolean online = Bukkit.getOfflinePlayer(uu).isOnline();

      List<String> lore = new ArrayList<>();
      lore.add("Player ID: " + Bukkit.getOfflinePlayer(uu).getName());
      lore.add("Andrew ID: " + andrewID);
      lore.add("Name: " + name);
      lore.add("UUID: " + uuid);
      lore.add("Discord: " + discord);
      lore.add("Term: " + term);
      lore.add("Online: " + online.toString());
      String hwStatus = getHWStatus(andrewID);
      lore.add("HW Status: " + hwStatus);

      VirtualUICallback callback = (VirtualUI callbackUI, Player player, ItemStack item, int slot, int index) -> {
        player.performCommand("hwadmin list " + andrewID);
      };

      ui.addItemStack(-1, uu, name, lore, hwStatus.contains("UNGRADED"), callback, callback, callback, callback);
    }
    q.close();
    return ui;
  }

  public static void gradeNext(Player grader) throws SQLException {
    DBConnect.Query q;
    DBConnect.Query qq;
    DBConnect.Query qqq;
    DBConnect db = Main.db;
    String term = Main.term;

    // List all student andrewID(varchar(200)) and uuid(varchar(200)
    q = db.queryDB("SELECT andrewID, name, uuid FROM intro2mc_student;");
    boolean found = false;
    while (q.rs.next()) {
      String andrewID = q.rs.getString("andrewID");
      String name = q.rs.getString("name");
      String uuid = q.rs.getString("uuid");
      UUID uu = UUID.fromString(uuid);
      Boolean online = Bukkit.getOfflinePlayer(uu).isOnline();

      String hwStatus = getHWStatus(andrewID);
      if (hwStatus.contains("UNGRADED")) {
        grader.sendMessage("Grading " + name + " (" + andrewID + ")");
        // Now find which homework is ungraded
        qq = db.queryDB("SELECT id, name, description FROM intro2mc_assignment WHERE term = ? ORDER BY created_at ASC;", term);
        while (qq.next()) {
          int assignmentIndex = qq.getInt("id");
          String hwName = qq.getString("name");
          String desc = qq.getString("description");
          // getting submissions
          qqq = db.queryDB(
              "SELECT student_id, details, grade, created_at, updated_at FROM intro2mc_submission WHERE assignment_id = ? AND student_id = ? ORDER BY created_at ASC;",
              assignmentIndex, andrewID);
          while (qqq.next()) {
            String grade = qqq.getString("grade");
            String details = qqq.getString("details");
            if (grade.equals("U")) {
              grader.sendMessage("Grading " + hwName + "(" + desc + ") for " + name + " (" + andrewID + ")");
              try {
                YamlConfiguration temp = new YamlConfiguration();
                temp.loadFromString(details);
                Location loc = temp.getLocation("location");
                // teleport grader to location
                grader.teleport(loc);
                // send grader clickable text executing /hwadmin grade <hwName> <andrewID> pass or /hwadmin grade <hwName> <andrewID> redo
                BaseComponent pass = new ComponentBuilder("[Pass]").create()[0];
                pass.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/hwadmin grade " + hwName + " " + andrewID + " pass"));
                pass.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder("Click to grade as pass").create()));
                BaseComponent redo = new ComponentBuilder("[Redo]").create()[0];
                redo.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/hwadmin grade " + hwName + " " + andrewID + " redo"));
                redo.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder("Click to grade as redo").create()));
                grader.spigot().sendMessage(new ComponentBuilder("Grade as: ").append(pass).append(" or ").append(redo).create());
                found = true;

              } catch (Exception e) {
                grader.sendMessage("Error in grading " + hwName + " for " + name + " (" + andrewID + ")");
              }
              if (found) break;
            }
          }
          qqq.close();
          if (found) break;
        }
        qq.close();
        if (found) break;
      }
    }
    q.close();
    if (!found) {
      grader.sendMessage("No more ungraded homeworks.");
    }
    q.close();
  }

  public static String getHWStatus(String andrewID) throws SQLException {
    DBConnect.Query q;
    DBConnect db = Main.db;
    String term = Main.term;

    q = db.queryDB("SELECT id, name, description, term, userSubmittable, gradeReleased FROM intro2mc_assignment " +
        "WHERE term = ? ORDER BY created_at ASC;", term);

    Boolean allGraded = true;
    int hwPassed = 0;
    while (q.next()) {
      int assignmentIndex = q.getInt("id");
      // String assignmentName = q.getString("name");
      // String desc = q.getString("description");
      // String assignmentTerm = q.getString("term");
      // Boolean submittable = q.getBoolean("userSubmittable");
      // Boolean gradeReleased = q.getBoolean("gradeReleased");

      // getting submissions
      DBConnect.Query qq = db.queryDB(
          "SELECT student_id, details, grade, created_at, updated_at FROM intro2mc_submission WHERE assignment_id = ? AND student_id = ? ORDER BY grade ASC;",
          assignmentIndex, andrewID);
      while (qq.next()) {
        // String studentID = qq.getString("student_id");
        // String details = qq.getString("details");
        String grade = qq.getString("grade");
        String gradeInterpretation;
        switch (grade) {
          case "U":
            gradeInterpretation = "UNGRADED";
            break;
          case "P":
            gradeInterpretation = "PASS";
            break;
          case "R":
            gradeInterpretation = "REDO";
            break;
          default:
            gradeInterpretation = "ERROR";
            break;
        }
        if (gradeInterpretation.equals("ERROR") || gradeInterpretation.equals("UNGRADED")) {
          allGraded = false;
        } else if (gradeInterpretation.equals("PASS")) {
          hwPassed++;
        }
      }
      qq.close();
    }
    q.close();

    if (!allGraded) {
      return "UNGRADED";
    }
    return "PASSED " + String.valueOf(hwPassed);
  }

  public static VirtualUI getHWUI(Player sender, OfflinePlayer p) throws SQLException {
    DBConnect.Query q;
    DBConnect db = Main.db;
    String term = Main.term;

    // START SHOWING VirtualUI
    // check if student in system
    q = db.queryDB("SELECT andrewID, name, discord FROM intro2mc_student WHERE uuid = ?;", p.getUniqueId().toString());
    if (!q.next()) {
      sender.sendMessage(
          "The player " + p.getName()
              + " is not registered for the system! Please contact the instructors if you believe this is a mistake.");
      q.close();
      return null;
    }
    String andrewID = q.getString("andrewID");
    String studentName = q.getString("name");
    String discord = q.getString("discord");
    q.close();

    // getting assignments
    VirtualUI ui = new VirtualUI("MCStudio - " + p.getName(), 54);
    q = db.queryDB("SELECT id, name, description, term, userSubmittable, gradeReleased FROM intro2mc_assignment " +
        "WHERE term = ? ORDER BY created_at ASC;", term);

    ArrayList<String> lore = new ArrayList<>();
    lore.add("Andrew ID: " + andrewID);
    lore.add("Student Name: " + studentName);
    lore.add("UUID: " + p.getUniqueId());
    lore.add("Discord: " + discord);

    ui.addItemStack(-1, p.getUniqueId(), p.getName(), lore, false, null, null, null, null);
    ui.addItemStack(-1, Material.GRAY_STAINED_GLASS_PANE, "", null, false, null, null, null, null);

    List<ItemStack> items = new ArrayList<>();
    while (q.next()) {
      int assignmentIndex = q.getInt("id");
      String assignmentName = q.getString("name");
      String desc = q.getString("description");
      String assignmentTerm = q.getString("term");
      Boolean submittable = q.getBoolean("userSubmittable");
      Boolean gradeReleased = q.getBoolean("gradeReleased");

      List<String> assignmentLore = new ArrayList<>();
      assignmentLore.add("Description: " + desc);
      assignmentLore.add("Term: " + assignmentTerm);
      assignmentLore.add("Achievement Based: " + !submittable);
      assignmentLore.add("Grade Released: " + gradeReleased);
      assignmentLore.add("");

      Boolean submittedOnce = false;

      // getting submissions
      DBConnect.Query qq = db.queryDB(
          "SELECT student_id, details, grade, created_at, updated_at FROM intro2mc_submission WHERE assignment_id = ? AND student_id = ? ORDER BY grade ASC;",
          assignmentIndex, andrewID);
      while (qq.next()) {
        String studentID = qq.getString("student_id");
        String details = qq.getString("details");
        String grade = qq.getString("grade");
        String gradeInterpretation = "Error";
        switch (grade) {
          case "U":
            gradeInterpretation = "UNGRADED";
            break;
          case "P":
            gradeInterpretation = "PASS";
            break;
          case "R":
            gradeInterpretation = "REDO";
            break;
          default:
            gradeInterpretation = "ERROR";
            break;
        }
        String submissionTime = qq.getString("created_at");
        String updateTime = qq.getString("updated_at");
        submittedOnce = true;

        assignmentLore.add("Submission Time: " + submissionTime);
        assignmentLore.add("Update Time: " + updateTime);
        try {
          YamlConfiguration temp = new YamlConfiguration();
          temp.loadFromString(details);
          Location loc = temp.getLocation("location");
          assignmentLore.add("Location: (" + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ() + ")");
        } catch (Exception e) {
        }
        assignmentLore.add("Grade: " + gradeInterpretation);
        assignmentLore.add("");
      }
      qq.close();
      if (submittedOnce) {
        items.add(ui.getItemStack(Material.MAP, assignmentName, assignmentLore, gradeReleased));
      } else {
        assignmentLore.add("No submissions yet!");
        items.add(ui.getItemStack(Material.PAPER, assignmentName, assignmentLore, gradeReleased));
      }
    }
    q.close();

    items.sort((a, b) -> {
      String aName = a.getItemMeta().getDisplayName();
      String bName = b.getItemMeta().getDisplayName();
      return aName.compareTo(bName);
    });
    for (ItemStack item : items) {
      String itemName = item.getItemMeta().getDisplayName();
      if (itemName.lastIndexOf(ChatColor.COLOR_CHAR) != -1) {
        itemName = itemName.substring(itemName.lastIndexOf(ChatColor.COLOR_CHAR) + 2);
      }
      String hwName = itemName;

      String command = "hw submit " + hwName;
      String commandRight = "hwadmin tp " + hwName + " " + andrewID;
      String commandShiftLeft = "hwadmin grade " + hwName + " " + andrewID + " " + "pass";
      String commandShiftRight = "hwadmin grade " + hwName + " " + andrewID + " " + "redo";

      // check if user can submit
      q = db.queryDB("SELECT id FROM intro2mc_assignment WHERE name = ? AND term = ? AND userSubmittable = true;",
          hwName,
          term);
      if (!q.next()) {
        VirtualUICallback cb = (VirtualUI _ui, Player _player, ItemStack _item, int _slot, int _index) -> {
          _player.sendMessage("Assignment " + hwName + " is not submittable.");
        };
        ui.addItemStack(-1, item, cb, cb, cb, cb);
      } else {
        // add callback
        VirtualUICallback cb = (VirtualUI _ui, Player _player, ItemStack _item, int _slot, int _index) -> {
          Bukkit.dispatchCommand(_player, command);
        };
        VirtualUICallback cbRight = (VirtualUI _ui, Player _player, ItemStack _item, int _slot, int _index) -> {
          Bukkit.dispatchCommand(_player, commandRight);
        };
        VirtualUICallback cbShiftLeft = (VirtualUI _ui, Player _player, ItemStack _item, int _slot, int _index) -> {
          Bukkit.dispatchCommand(_player, commandShiftLeft);
        };
        VirtualUICallback cbShiftRight = (VirtualUI _ui, Player _player, ItemStack _item, int _slot, int _index) -> {
          Bukkit.dispatchCommand(_player, commandShiftRight);
        };
        ui.addItemStack(-1, item, VirtualUICallback.withConfirm("Submit the Homework?", "/" + command, cb), VirtualUICallback.withConfirm("Teleport to the Homework?", "/" + commandRight, cbRight), VirtualUICallback.withConfirm("Grade the Homework as Pass?", "/" + commandShiftLeft, cbShiftLeft), VirtualUICallback.withConfirm("Grade the Homework as Redo?", "/" + commandShiftRight, cbShiftRight));
      }
      q.close();
    }

    ui.addLineBreak(1, Material.GRAY_STAINED_GLASS_PANE);

    // getting class session
    q = db.queryDB("SELECT date, code, accepting FROM intro2mc_classsession WHERE term = ?;", term);
    int totalClassSession = 0;
    int totalAttended = 0;
    int totalExcused = 0;
    while (q.next()) {
      totalClassSession++;
      String date = q.getString("date");
      // String code = q.getString("code");
      Boolean accepting = q.getBoolean("accepting");
      List<String> classSessionLore = new ArrayList<String>();
      classSessionLore.add("Accepting Attendance: " + accepting + (accepting ? " (click to submit attendance)" : ""));

      // getting attendance
      DBConnect.Query qq = db.queryDB(
          "SELECT created_at, updated_at, excused, reason FROM intro2mc_attendance WHERE student_id = ? AND term = ? AND classSession_id = ?;",
          andrewID, term, date);
      int count = 0;
      Boolean isExcused = false;
      if (qq.next()) {
        count++;
        Timestamp createdAt = qq.getTimestamp("created_at");
        Timestamp updatedAt = qq.getTimestamp("updated_at");
        Boolean excused = qq.getBoolean("excused");
        isExcused = isExcused || excused;
        String reason = qq.getString("reason");

        classSessionLore.add("Attended: true");
        classSessionLore.add("Attended At: " + createdAt);
        classSessionLore.add("Is Excused: " + excused);
        if (excused) {
          classSessionLore.add("Excused Reason: " + reason);
        }
        classSessionLore.add("");
      }
      qq.close();
      if (count == 0) {
        classSessionLore.add("Attended: false");
        classSessionLore.add("");
      }
      if (count != 0) {
        totalAttended++;
        if (isExcused) {
          totalExcused++;
        }
      }

      ui.addItemStack(-1, count == 0 ? Material.RED_CONCRETE : Material.GREEN_CONCRETE,
          "Attendance: " + date.toString(), classSessionLore, accepting, null, null, null, null);
    }
    q.close();


    // refresh lore
    lore.add("Attendance: " + totalAttended + "/" + totalClassSession);
    lore.add("Excused: " + totalExcused + "/" + totalAttended);
    ui.addItemStack(0, p.getUniqueId(), p.getName(), lore, false, null, null, null, null);

    return ui;
  }
}
