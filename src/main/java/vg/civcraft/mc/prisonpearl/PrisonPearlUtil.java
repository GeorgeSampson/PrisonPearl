package vg.civcraft.mc.prisonpearl;

import java.util.UUID;
import java.util.concurrent.Callable;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import vg.civcraft.mc.prisonpearl.managers.PrisonPearlManager;
import vg.civcraft.mc.prisonpearl.managers.SummonManager;

public class PrisonPearlUtil {

	private static PrisonPearlManager manager;
	private static SummonManager summon;
	
	private static Thread mainThread;
	
	public PrisonPearlUtil() {
		manager = PrisonPearlPlugin.getPrisonPearlManager();
		summon = PrisonPearlPlugin.getSummonManager();
		mainThread = Thread.currentThread();
	}
	
	public static boolean respawnPlayerCorrectly(Player p) {
		return respawnPlayerCorrectly(p, null);
	}
	
	/**
	 * 
	 * @param p
	 * @param pp
	 * @param passPearl Will mainly just deal if a player was just freed.
	 * @return Return false if the player was not tpped in anyway.
	 */
	public static boolean respawnPlayerCorrectly(Player p, PrisonPearl passPearl) { 
		// We want this method to deal with all cases: Respawn on death, Respawn on summoning, returning,
		// different shards transport, everything. 
		
		UUID uuid = p.getUniqueId();
		boolean freeToPearl = PrisonPearlConfig.shouldTpPearlOnFree();
		PrisonPearl pp = manager.getByImprisoned(uuid);
		if (manager.isImprisoned(uuid)) {
			// This part will deal for when bettershards and mercury are not enabled.
			// This still needs work on, should get reports from non mercury/ BetterShards Servers and they should say whats wrong.
			if (summon.isSummoned(p)) {
				Summon s = summon.getSummon(p);
				if (s.isToBeReturned()) {
					PrisonPearlPlugin.doDebug("Player {0} was summoned and is being returned", uuid);
					if (PrisonPearlConfig.getShouldPPReturnKill()) {
						p.setHealth(0);
					}
					Location newLoc = s.getReturnLocation();
					newLoc.setY(newLoc.getY() + 1);
					p.teleport(newLoc);
				} else if (s.isJustCreated()) {
					PrisonPearlPlugin.doDebug("Player {0} was just summoned!", uuid);
					if (PrisonPearlConfig.shouldPpsummonClearInventory()) {
						dropInventory(p, p.getLocation(), PrisonPearlConfig.shouldPpsummonLeavePearls());
					}
					p.teleport(s.getPearlLocation());
				} else {
					PrisonPearlPlugin.doDebug("Player {0} is summoned, but nothing to do", uuid);
				}
			} else if (!p.getWorld().equals(manager.getPrisonSpawnLocation().getWorld())) {
				PrisonPearlPlugin.doDebug("Player {0} is imprisoned; respawning back into the prison world", uuid);
				p.teleport(manager.getPrisonSpawnLocation());
			} else {
				PrisonPearlPlugin.doDebug("Respawning player {0} based on is-imprisoned catchall condition", uuid);
				Location newLoc = manager.getPrisonSpawnLocation();
				p.teleport(newLoc);
			}
			return true;
		} else if (passPearl != null && freeToPearl) {
			// pp is null b/c manager has already removed it due to /ppfree or throwing the pearl.
			// so use passPearl instead to free the player.
			PrisonPearlPlugin.doDebug("Player {0} was freed; teleporting to pearl-toss free location", uuid);
			if(PrisonPearlConfig.shouldPpsummonClearInventory()) {
				dropInventory(p, p.getLocation(), PrisonPearlConfig.shouldPpsummonLeavePearls());
			}
			if (passPearl.getLocation().getY() < 1.0) {
				p.teleport(passPearl.getLocation().add(0,1.0,0));
			} else {
				p.teleport(passPearl.getLocation());
			}
			return true;
		} else if (passPearl == null && pp != null) {
			PrisonPearlPlugin.doDebug("Player {0} is pearled and not freed, send them back to prison", uuid);
			p.teleport(manager.getPrisonSpawnLocation());
		} else {
			PrisonPearlPlugin.doDebug("Player {0} hit up for respawn but no dice -- {1}, {2}", uuid, pp, passPearl);
		}
		return false;
	}
	
	 /**
     * @param location - the location to serialize into user-friendly text.
     * @return the serialized user-friendly string representing location.
     */
    public static String serializeLocation(Location location) {
    	return String.format("%s, (%d, %d, %d)", location.getWorld().getName(),
    							location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }
    
    public static void delayedTp(final Player player, final Location loc, final boolean dropInventory) {
		final boolean respawn = loc != null;
		final Location oldLoc = player.getLocation();
		if (respawn) {
			player.setHealth(0.0);
		}
		Bukkit.getScheduler().callSyncMethod(PrisonPearlPlugin.getInstance(), new Callable<Void>() {
			public Void call() {
				if (!respawn) {
					player.teleport(loc);
				}
				if (dropInventory) {
					dropInventory(player, oldLoc, PrisonPearlConfig.shouldPpsummonLeavePearls());
				}
				return null;
			}
		});
	}
    
    public static void dropInventory(Player player, Location loc, boolean leavePearls) {
		if (loc == null) {
			loc = player.getLocation();
		}
		final World world = loc.getWorld();
		Inventory inv = player.getInventory();
		int end = inv.getSize();
		for (int i = 0; i < end; ++i) {
			final ItemStack item = inv.getItem(i);
			if (item == null) {
				continue;
			}
			if (leavePearls && item.getType().equals(Material.ENDER_PEARL)
					&& item.getDurability() == 0) {
				continue;
			}
			inv.clear(i);
			if (!isMainThread(Thread.currentThread())) {
				final Location l = loc;
				Bukkit.getScheduler().runTask(PrisonPearlPlugin.getInstance(), new Runnable() {

					@Override
					public void run() {
						world.dropItemNaturally(l, item);
					}
					
				});
			}
			else
				world.dropItemNaturally(loc, item);
		}
	}
    
    public static boolean isMainThread(Thread t) {
    	return t.equals(mainThread);
    }
}