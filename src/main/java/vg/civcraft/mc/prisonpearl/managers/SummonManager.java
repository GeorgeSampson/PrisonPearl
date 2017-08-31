package vg.civcraft.mc.prisonpearl.managers;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import vg.civcraft.mc.prisonpearl.PrisonPearl;
import vg.civcraft.mc.prisonpearl.PrisonPearlConfig;
import vg.civcraft.mc.prisonpearl.PrisonPearlPlugin;
import vg.civcraft.mc.prisonpearl.PrisonPearlUtil;
import vg.civcraft.mc.prisonpearl.Summon;
import vg.civcraft.mc.prisonpearl.database.interfaces.ISummonStorage;

public class SummonManager {

	private ISummonStorage storage;
	private PrisonPearlManager pearls;

	public SummonManager() {
		storage = PrisonPearlPlugin.getDBHandler().getStorageHandler().getSummonStorage();
		pearls = PrisonPearlPlugin.getPrisonPearlManager();
		Bukkit.getScheduler().scheduleSyncRepeatingTask(PrisonPearlPlugin.getInstance(), new Runnable() {
			public void run() {
				inflictSummonDamage();
			}
		}, 0, PrisonPearlConfig.getSummonDamageTicks());
	}

	public boolean isSummoned(Player p) {
		return isSummoned(p.getUniqueId());
	}

	public boolean isSummoned(UUID uuid) {
		return storage.isSummoned(uuid);
	}

	public boolean isSummoned(PrisonPearl pp) {
		return isSummoned(pp.getImprisonedId());
	}

	/**
	 * This method will handle getting the player from another server and
	 * current server if it needs to.
	 * 
	 * @param pearl
	 */
	public boolean summonPlayer(PrisonPearl pearl) {
		final Player pearled = pearl.getImprisonedPlayer();
		if (pearled != null) {
			Summon s = new Summon(pearl.getImprisonedId(), pearled.getLocation(), pearl);
			addSummonPlayer(s); // We need to add the summon now so that respawn method can find the Summon Object.
			// Fucking turtles right.
			s.setJustCreated(true);
			PrisonPearlUtil.respawnPlayerCorrectly(pearled);
			s.setJustCreated(false);
			return true;
		}
		return false;
	}

	/**
	 * This method should be used if for example we earlier requested a player
	 * be summoned from another server and only now they came on and we have the
	 * proper details.
	 * 
	 * @param s
	 */
	public void addSummonPlayer(Summon s) {
		storage.addSummon(s);
	}

	public boolean returnPlayer(PrisonPearl pearl) {
		Player pearled = pearl.getImprisonedPlayer();
		if (pearled != null) {
			Summon s = getSummon(pearl);
			s.setToBeReturned(true);
			PrisonPearlUtil.respawnPlayerCorrectly(pearled);
			s.setToBeReturned(false);
			storage.removeSummon(s);
			s.setTime(System.currentTimeMillis());
			return true;
		}
		return false;
	}

	public boolean removeSummon(PrisonPearl pearl) {
		Summon s = getSummon(pearl);
		if (s == null)
			return false;
		storage.removeSummon(s);
		return true;
	}
	
	public Summon getSummon(UUID uuid) {
		return storage.getSummon(uuid);
	}

	public Summon getSummon(Player p) {
		return getSummon(p.getUniqueId());
	}

	public Summon getSummon(PrisonPearl pearl) {
		return storage.getSummon(pearl.getImprisonedId());
	}

	private void inflictSummonDamage() {
		Map<Player, Double> inflictDmg = new HashMap<Player, Double>();
		Iterator<Entry<UUID, Summon>> i = storage.getAllSummons().entrySet().iterator();
		while (i.hasNext()) {
			Summon summon = i.next().getValue();
			PrisonPearl pp = pearls.getByImprisoned(summon.getUUID());
			if (pp == null) {
				System.err.println("Somehow " + summon.getUUID() + " was summoned but isn't imprisoned");
				i.remove();
				storage.removeSummon(summon.getUUID());
				continue;
			}

			Player player = pp.getImprisonedPlayer();
			if (player == null)
				continue;

			Location pploc = pp.getLocation();
			Location playerloc = player.getLocation();

			if (pploc.getWorld() != playerloc.getWorld() || pploc.distance(playerloc) > summon.getMaxDistance()) {
				inflictDmg.put(player, (double) summon.getAmountDamage());
			}
		}
		for (Map.Entry<Player, Double> entry : inflictDmg.entrySet()) {
			final Player player = entry.getKey();
			final Double damage = entry.getValue();
			player.damage(damage);
		}
	}
}
