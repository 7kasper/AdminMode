package nl.kaspermuller.adminmode;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.util.List;
import java.util.UUID;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;
import java.util.stream.Collectors;

/***
 * **AdminMode**
 * This plugin adds an adminmode.
 * Adminmode is an easy way for admins to go
 * into advanced spectator mode and come out in the 
 * place they left.
 * 
 * @author 7kasper
 *
 */
public class AdminMode extends JavaPlugin implements Listener {

    private File dataFile;
    private FileConfiguration data;

	@Override
	public void onEnable() {
		getServer().getConsoleSender().sendMessage("Enabled §aAdminMode§r plugin :D");
		getServer().getPluginManager().registerEvents(this, this);
		saveDefaultData();
	}

    //=====================================================\\
    //				        Config  	     	  		   \\
    //=====================================================\\

	private void reloadData() {
	    if (dataFile == null) {
	    	dataFile = new File(getDataFolder(), "data.yml");
	    }
	    data = YamlConfiguration.loadConfiguration(dataFile);
	    Reader defConfigStream;
		try {
			defConfigStream = new InputStreamReader(this.getResource("data.yml"), "UTF8");
		    if (defConfigStream != null) {
		        YamlConfiguration defConfig = YamlConfiguration.loadConfiguration(defConfigStream);
		        data.setDefaults(defConfig);
		    }
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}
	}

	private void saveDefaultData() {
	    if (dataFile == null) {
	    	dataFile = new File(getDataFolder(), "data.yml");
	    }
	    if (!dataFile.exists()) {            
	         this.saveResource("data.yml", false);
	     }
	}

	private FileConfiguration getData() {
	    if (data == null) {
	        reloadData();
	    }
	    return data;
	}

	public void saveData() {
	    if (data == null || dataFile == null) {
	        return;
	    }
	    try {
	        getData().save(dataFile);
	    } catch (IOException ex) {
	        getServer().getLogger().log(Level.SEVERE, "Could not save config to " + dataFile, ex);
	    }
	}

    //=====================================================\\
    //				        Commands  	     	  		   \\
    //=====================================================\\

	/**
	 * Handle adminmode toggle command.
	 */
	@Override
	public boolean onCommand(CommandSender s, Command cmd, String label, String[] args) {
		if (cmd.getLabel().equals("adminmode") && s.hasPermission("adminmode.use")) {
		    if (s instanceof Player) {
		        Player p = (Player) s;
		        if (isInAdminMode(p)) {
		        	p.teleport(getAdminModeEnterLocation(p));
		        	p.setGameMode(GameMode.SURVIVAL);
		        	getData().set("locations." + p.getUniqueId(), null);
		        	saveData();
		        	s.sendMessage("§9Exited adminmode!");
		        } else {
		        	getData().set("locations." + p.getUniqueId(), p.getLocation());
		        	saveData();
		        	p.setGameMode(GameMode.SPECTATOR);
		        	if (args.length > 0) {
		        		Player target = getServer().getPlayer(args[0]);
		        		if (target != null) {
		        			p.setSpectatorTarget(target);
		        		} else {
		        			s.sendMessage("§4Player not found!");
		        		}
		        	}
		        	s.sendMessage("§9Entered adminmode!");
		        }
		        return true;
		    } else {
		        s.sendMessage("§4Adminmode is only for admin players!");
		        return false;
		    }
		    
		}
		if (cmd.getLabel().equals("enderchest") && s.hasPermission("adminmode.enderchest")) {
		    if (s instanceof Player) {
		        Player p = (Player) s;
		        Player target = null;
		        if (p.getSpectatorTarget() != null && p.getSpectatorTarget().getType() == EntityType.PLAYER) {
		        	target = (Player) p.getSpectatorTarget();
		        }
		        if (args.length > 0) {
		        	target = getServer().getPlayer(args[0]);
		        }
		        if (target != null) {
		        	p.closeInventory();
		        	p.openInventory(target.getEnderChest());
		        	return true;
		        } else {
		        	s.sendMessage("§4Please specify or spectate a player!");
		        	return false;
		        }
		    } else {
		        s.sendMessage("§4Enderchest is only for admin players!");
		        return false;
		    }
		}
		return false;
	}

	@Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return getServer().getOnlinePlayers().stream().map(p -> p.getName()).collect(Collectors.toList());
    }
	
    //=====================================================\\
    //				        Listeners  	     	  		   \\
    //=====================================================\\
	
	/**
	 * Open enderchest listener.
	 * @param e
	 */
	@EventHandler
	public void onClick(PlayerInteractEvent e) {
		if (isInAdminMode(e.getPlayer())) {
			if (e.getAction() != Action.PHYSICAL) {
				// Check if following another player.
				Entity target = e.getPlayer().getSpectatorTarget();
				if (target != null && target.getType() == EntityType.PLAYER) {
					e.getPlayer().closeInventory();
//					e.getPlayer().openInventory(((HumanEntity) target).getEnderChest());
					e.getPlayer().openInventory(((HumanEntity) target).getInventory());
				}
			}
		}
	}

	/**
	 * Function to allow spectator gamemode to interact with inventories.
	 * (Only for adminmode and other plugins can still override later on).
	 * @param e
	 */
	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
	public void onInventoryClick(InventoryClickEvent e) {
		if (e.getWhoClicked() instanceof Player && isInAdminMode((Player) e.getWhoClicked())) {
			e.setCancelled(false);
		}
	}

	/**
	 * Upon a player quitting make sure the inventory is properly closed
	 * because some admins in adminmode might be eavesdropping.
	 * @param e
	 */
	@EventHandler
	public void onQuit(PlayerQuitEvent e) {
        for (HumanEntity viewer : e.getPlayer().getInventory().getViewers()) {
            if (viewer instanceof Player) {
                if (isInAdminMode((Player) viewer)) {
                	viewer.closeInventory();
                }
            }
        }
        for (HumanEntity viewer : e.getPlayer().getEnderChest().getViewers()) {
            if (viewer instanceof Player) {
                if (isInAdminMode((Player) viewer)) {
                	viewer.closeInventory();
                }
            }
        }
	}

    //=====================================================\\
    //				       	   API  	     	  		   \\
    //=====================================================\\

	/**
	 * Checks if a UUID is currently in adminmode.
	 * @param admin
	 * @return true, if the UUID is in adminmode.
	 */
	public boolean isInAdminMode(UUID admin) {
		return getData().get("locations." + admin) != null;
	}

	/**
	 * Checks if player is currently in adminmode.
	 * @param admin
	 * @return true, if the player is in adminmode.
	 */
	public boolean isInAdminMode(Player admin) {
		return isInAdminMode(admin.getUniqueId());
	}

	/**
	 * Gets the location where admin of UUID entered adminmode.
	 * @param admin
	 * @return the location upon entering adminmode.
	 */
	public Location getAdminModeEnterLocation(UUID admin) {
		return getData().getLocation("locations." + admin);
	}

	/**
	 * Gets the location where admin entered adminmode.
	 * @param admin
	 * @return the location upon entering adminmode.
	 */
	public Location getAdminModeEnterLocation(Player admin) {
		return getAdminModeEnterLocation(admin.getUniqueId());
	}

}
