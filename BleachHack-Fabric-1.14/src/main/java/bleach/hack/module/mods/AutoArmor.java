package bleach.hack.module.mods;

import java.util.HashMap;
import java.util.Map.Entry;

import com.google.common.eventbus.Subscribe;

import bleach.hack.event.events.EventTick;
import bleach.hack.module.Category;
import bleach.hack.module.Module;
import bleach.hack.setting.base.SettingSlider;
import bleach.hack.setting.base.SettingToggle;
import bleach.hack.utils.BleachQueue;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.container.SlotActionType;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.ProtectionEnchantment;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ElytraItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.packet.UpdateSelectedSlotC2SPacket;

public class AutoArmor extends Module {

	private int tickDelay = 0;

	public AutoArmor() {
		super("AutoArmor", KEY_UNBOUND, Category.PLAYER, "Automatically equips armor",
				new SettingToggle("Anti Break", false).withDesc("Unequips your armor when its about to break"),
				new SettingToggle("Legit Equip", false).withDesc("Freezes you when it would be impossible to equip the armor without going in your inventory"),
				new SettingToggle("Prefer Elytra", false).withDesc("Equips elytras instead of chestplates when possible"),
				new SettingToggle("Delay", true).withDesc("Adds a delay between equipping armor pieces to make it bypass more anticheats").withChildren(
						new SettingSlider("Delay", 0, 20, 1, 0).withDesc("How many ticks between putting on armor pieces")));
	}

	@Subscribe
	public void onTick(EventTick event) {
		if (!BleachQueue.isEmpty("autoarmor_equip"))
			return;

		if (tickDelay > 0) {
			tickDelay--;
			return;
		}

		tickDelay = (getSetting(3).asToggle().state ? (int) getSetting(3).asToggle().getChild(0).asSlider().getValue() : 0);

		/* [Slot type, [Armor slot, Armor prot, New armor slot, New armor prot]] */
		HashMap<EquipmentSlot, int[]> armorMap = new HashMap<>(4);
		armorMap.put(EquipmentSlot.FEET, new int[] { 36, getProtection(mc.player.inventory.getInvStack(36)), -1, -1 });
		armorMap.put(EquipmentSlot.LEGS, new int[] { 37, getProtection(mc.player.inventory.getInvStack(37)), -1, -1 });
		armorMap.put(EquipmentSlot.CHEST, new int[] { 38, getProtection(mc.player.inventory.getInvStack(38)), -1, -1 });
		armorMap.put(EquipmentSlot.HEAD, new int[] { 39, getProtection(mc.player.inventory.getInvStack(39)), -1, -1 });

		for (int s = 0; s < 36; s++) {
			int prot = getProtection(mc.player.inventory.getInvStack(s));

			if (prot > 0) {
				EquipmentSlot slot = (mc.player.inventory.getInvStack(s).getItem() == Items.ELYTRA
						? EquipmentSlot.CHEST : ((ArmorItem) mc.player.inventory.getInvStack(s).getItem()).getSlotType());

				for (Entry<EquipmentSlot, int[]> e: armorMap.entrySet()) {
					if (e.getKey() == slot) {
						if (prot > e.getValue()[1] && prot > e.getValue()[3]) {
							e.getValue()[2] = s;
							e.getValue()[3] = prot;
						}
					}
				}
			}
		}

		for (Entry<EquipmentSlot, int[]> e: armorMap.entrySet()) {
			if (e.getValue()[2] != -1) {
				if (e.getValue()[1] == -1 && e.getValue()[2] < 9) {
					if (e.getValue()[2] != mc.player.inventory.selectedSlot) {
						mc.player.inventory.selectedSlot = e.getValue()[2];
						mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(e.getValue()[2]));
					}

					mc.interactionManager.method_2906(mc.player.container.syncId, 36 + e.getValue()[2], 1, SlotActionType.QUICK_MOVE, mc.player);
				} else if (mc.currentScreen instanceof InventoryScreen || mc.currentScreen == null) {
					/* Convert inventory slots to container slots */
					int armorSlot = (e.getValue()[0] - 34) + (39 - e.getValue()[0]) * 2;
					int newArmorslot = e.getValue()[2] < 9 ? 36 + e.getValue()[2] : e.getValue()[2];

					mc.interactionManager.method_2906(mc.player.container.syncId, newArmorslot, 0, SlotActionType.PICKUP, mc.player);
					mc.interactionManager.method_2906(mc.player.container.syncId, armorSlot, 0, SlotActionType.PICKUP, mc.player);

					if (e.getValue()[1] != -1)
						mc.interactionManager.method_2906(mc.player.container.syncId, newArmorslot, 0, SlotActionType.PICKUP, mc.player);
				}

				return;
			}
		}
	}

	private int getProtection(ItemStack is) {
		if (is.getItem() instanceof ArmorItem || is.getItem() == Items.ELYTRA) {
			int prot = 0;
			
			if (is.getItem() == Items.ELYTRA) {
				if (!ElytraItem.isUsable(is))
					return 0;
				
				if (getSetting(2).asToggle().state)
					prot = 32767;
				else
					prot = 1;
			}
			
			if (is.hasEnchantments()) {
				for (Entry<Enchantment, Integer> e: EnchantmentHelper.getEnchantments(is).entrySet()) {
					if (e.getKey() instanceof ProtectionEnchantment)
						prot += e.getValue();
				}
			}

			return (is.getItem() instanceof ArmorItem ? ((ArmorItem) is.getItem()).getProtection() : 0) + prot;
		} else if (!is.isEmpty()) {
			return 0;
		}

		return -1;
	}
}
