package me.mrCookieSlime.sensibletoolbox.blocks.machines;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.DyeColor;
import org.bukkit.Effect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.material.MaterialData;
import org.bukkit.plugin.Plugin;

import me.desht.dhutils.MiscUtil;
import me.desht.dhutils.cuboid.Cuboid;
import me.mrCookieSlime.sensibletoolbox.SensibleToolboxPlugin;
import me.mrCookieSlime.sensibletoolbox.api.SensibleToolbox;
import me.mrCookieSlime.sensibletoolbox.api.gui.ButtonGadget;
import me.mrCookieSlime.sensibletoolbox.api.gui.CyclerGadget;
import me.mrCookieSlime.sensibletoolbox.api.gui.InventoryGUI;
import me.mrCookieSlime.sensibletoolbox.api.items.BaseSTBBlock;
import me.mrCookieSlime.sensibletoolbox.api.items.BaseSTBItem;
import me.mrCookieSlime.sensibletoolbox.api.items.BaseSTBMachine;
import me.mrCookieSlime.sensibletoolbox.api.util.BlockProtection;
import me.mrCookieSlime.sensibletoolbox.api.util.STBUtil;
import me.mrCookieSlime.sensibletoolbox.items.LandMarker;
import net.coreprotect.CoreProtect;
import net.coreprotect.CoreProtectAPI;

public class AutoBuilder extends BaseSTBMachine {

    private static final MaterialData md = STBUtil.makeColouredMaterial(Material.STAINED_CLAY, DyeColor.YELLOW);
    private static final int LANDMARKER_SLOT_1 = 10;
    private static final int LANDMARKER_SLOT_2 = 12;
    public static final int MODE_SLOT = 14;
    public static final int STATUS_SLOT = 15;
    private static final int START_BUTTON_SLOT = 53;
    private static final int MAX_DISTANCE = 5;

    private AutoBuilderMode buildMode;
    private Cuboid workArea;
    private int buildX, buildY, buildZ;
    private int invSlot;  // the inventory slot index (into getInputSlots()) being pulled from
    private BuilderStatus status = BuilderStatus.NO_WORKAREA;
    private int baseScuPerOp;
    private boolean limit = false;
    private static SensibleToolboxPlugin plugin = SensibleToolboxPlugin.getInstance();

    public AutoBuilder() {
        super();
        buildMode = AutoBuilderMode.CLEAR;
    }

    public CoreProtectAPI getCoreProtect() {
    	Plugin plugin = Bukkit.getServer().getPluginManager().getPlugin("CoreProtect");

    	// Check that CoreProtect is loaded
    	if (plugin == null || !(plugin instanceof CoreProtect)) {
    	    return null;
    	}

    	// Check that the API is enabled
    	CoreProtectAPI CoreProtect = ((CoreProtect)plugin).getAPI();
    	if (CoreProtect.isEnabled()==false){
    	    return null;
    	}

    	// Check that a compatible version of the API is loaded
    	if (CoreProtect.APIVersion() < 4){
    	    return null;
    	}

    	return CoreProtect;
    	}
    public AutoBuilder(ConfigurationSection conf) {
        super(conf);
        buildMode = AutoBuilderMode.valueOf(conf.getString("buildMode"));
    }

    @Override
    public YamlConfiguration freeze() {
        YamlConfiguration conf = super.freeze();
        conf.set("buildMode", buildMode.toString());
        return conf;
    }

    @Override
    public int[] getInputSlots() {
        return new int[] {};
    }

    @Override
    public int[] getOutputSlots() {
        return new int[0];
    }

    @Override
    protected boolean shouldPaintSlotSurrounds() {
        return false;
    }

    @Override
    public int[] getUpgradeSlots() {
        return new int[0];
    }

    @Override
    public int getUpgradeLabelSlot() {
        return -1;
    }

    public int getEnergyCellSlot() {
        return 45;
    }

    public int getChargeDirectionSlot() {
        return 36;
    }

    @Override
    public MaterialData getMaterialData() {
        return md;
    }

    @Override
    public String getItemName() {
        return "Quarry";
    }

    @Override
    public String[] getLore() {
        return new String[] {"Can Clear an area.","  Use Land Markersto define the area"};
    }


    @Override
    public boolean acceptsEnergy(BlockFace face) {
        return true;
    }

    @Override
    public boolean suppliesEnergy(BlockFace face) {
        return false;
    }

    @Override
    public int getMaxCharge() {
        return 10000;
    }

    @Override
    public int getChargeRate() {
        return 50;
    }

    @Override
    public InventoryGUI createGUI() {
        InventoryGUI gui = super.createGUI();

        gui.setSlotType(LANDMARKER_SLOT_1, InventoryGUI.SlotType.ITEM);
        setupLandMarkerLabel(gui, null, null);
        gui.setSlotType(LANDMARKER_SLOT_2, InventoryGUI.SlotType.ITEM);
        gui.addGadget(new AutoBuilderGadget(gui, STATUS_SLOT));
        gui.addGadget(new AutoBuilderGadget(gui, MODE_SLOT));
        gui.addGadget(new ButtonGadget(gui, START_BUTTON_SLOT, "Start", null, null, new Runnable() {
            @Override
            public void run() {
                if (getStatus() == BuilderStatus.RUNNING) {
                    stop(false);
                } else {
                    startup();
                    setLimit(true);
                }
            }
        }));
        ChatColor c = STBUtil.dyeColorToChatColor(status.getColor());
        gui.addLabel(ChatColor.WHITE + "Status: " + c + status, STATUS_SLOT, status.makeTexture(), status.getText());

        gui.addLabel("Building Inventory", 29, null);

        return gui;
    }

    public AutoBuilderMode getBuildMode() {
        return buildMode;
    }

    public void setBuildMode(AutoBuilderMode buildMode) {
        this.buildMode = buildMode;
        setStatus(workArea == null ? BuilderStatus.NO_WORKAREA : BuilderStatus.READY);
    }

    private void startup() {
        baseScuPerOp = getItemConfig().getInt("scu_per_op");
        setLimit(true);
        if (workArea == null) {
            BuilderStatus bs = setupWorkArea();
            if (bs != BuilderStatus.READY && !isLimited()) {
                setStatus(bs);
                return;
            }
        }
        if (getStatus().resetBuildPosition()) {
            buildX = workArea.getLowerX();
            buildY = getBuildMode().yDirection < 0 ? workArea.getUpperY() : workArea.getLowerY();
            buildZ = workArea.getLowerZ();
        }

        if (getBuildMode() != AutoBuilderMode.CLEAR ) {
            if (!initInventoryPointer()) {
                setStatus(BuilderStatus.NO_INVENTORY);
                return;
            }
        }


        setStatus(BuilderStatus.RUNNING);
    }

    private void stop(boolean finished) {
        setStatus(finished ? BuilderStatus.FINISHED : BuilderStatus.PAUSED);
        setLimit(false);
    }

    private boolean initInventoryPointer() {
        invSlot = -1;
        for (int slot = 0; slot < getInputSlots().length; slot++) {
            if (getInventoryItem(getInputSlots()[slot]) != null) {
                invSlot = slot;
                return true;
            }
        }
        return false;
    }

    public BuilderStatus getStatus() {
        return status;
    }

    private void setStatus(BuilderStatus status) {
        if (status != this.status) {
            this.status = status;
            ChatColor c = STBUtil.dyeColorToChatColor(status.getColor());
            getGUI().addLabel(ChatColor.WHITE + "Status: " + c + status, STATUS_SLOT, status.makeTexture(), status.getText());
            updateAttachedLabelSigns();
        }
    }

	@SuppressWarnings("deprecation")
	@Override
    public void onServerTick() {
        if (isRedstoneActive() && getStatus() == BuilderStatus.RUNNING && workArea != null) {
            Block b = getLocation().getWorld().getBlockAt(buildX, buildY, buildZ);
            double scuNeeded = 0.0;
            boolean advanceBuildPos = true;
            switch (getBuildMode()) {
                case CLEAR:
                    if (!SensibleToolbox.getBlockProtection().playerCanBuild(getOwner(), b, BlockProtection.Operation.BREAK)) {
                        setStatus(BuilderStatus.NO_PERMISSION);
                        return;
                    }
                    if (STBUtil.getMaterialHardness(b.getType()) < Double.MAX_VALUE) {
                        scuNeeded = baseScuPerOp * STBUtil.getMaterialHardness(b.getType());
                        if (scuNeeded > getCharge()) {
                            advanceBuildPos = false;
                        } else if (b.getType() != Material.AIR) {
                            b.getWorld().playEffect(b.getLocation(), Effect.STEP_SOUND, b.getType());
                            BaseSTBBlock stb = SensibleToolbox.getBlockAt(b.getLocation());
                            if (stb != null) {
                                stb.breakBlock(false);
                            } else {
                            	CoreProtectAPI api = getCoreProtect();
                            	String Quarry = "Quarry";
								api.logRemoval( Quarry, b.getLocation(), b.getType(), b.getData());
                                b.setType(Material.AIR);
                            }
                        }
                    }
                    break;
            }

            if (scuNeeded <= getCharge()) {
                setCharge(getCharge() - scuNeeded);
            }

            if (advanceBuildPos) {
                buildX++;
                if (buildX > workArea.getUpperX()) {
                    buildX = workArea.getLowerX();
                    buildZ++;
                    if (buildZ > workArea.getUpperZ()) {
                        buildZ = workArea.getLowerZ();
                        buildY += getBuildMode().getYDirection();
                        if (getBuildMode().getYDirection() < 0 && buildY < workArea.getLowerY()
                                || getBuildMode().getYDirection() > 0 && buildY > workArea.getUpperY()) {
                            // finished!
                            stop(true);
                        }
                    }
                }
            }
        }

        super.onServerTick();
    }
    @Override
    public void setInventoryItem(int slot, ItemStack item) {
        super.setInventoryItem(slot, item);
        if (slot == LANDMARKER_SLOT_1 || slot == LANDMARKER_SLOT_2) {
            BuilderStatus bs = setupWorkArea();
            setStatus(bs);
        }
    }

    private BuilderStatus setupWorkArea() {
        workArea = null;

        LandMarker lm1 = SensibleToolbox.getItemRegistry().fromItemStack(getInventoryItem(LANDMARKER_SLOT_1), LandMarker.class);
        LandMarker lm2 = SensibleToolbox.getItemRegistry().fromItemStack(getInventoryItem(LANDMARKER_SLOT_2), LandMarker.class);

        if (lm1 != null && lm2 != null) {
            Location loc1 = lm1.getMarkedLocation();
            Location loc2 = lm2.getMarkedLocation();
            if (!loc1.getWorld().equals(loc2.getWorld())) {
                return BuilderStatus.LM_WORLDS_DIFFERENT;
            }
            if (getLimit()) {
            		return BuilderStatus.LIMIT_REACHED;
            }
            Location ourLoc = getLocation();
            Cuboid w = new Cuboid(loc1, loc2);
            if (w.contains(ourLoc)) {
                return BuilderStatus.TOO_NEAR;
            }
            if (!w.outset(Cuboid.CuboidDirection.Both, MAX_DISTANCE).contains(ourLoc)) {
                return BuilderStatus.TOO_FAR;
            }
            workArea = w;
            setupLandMarkerLabel(getGUI(), loc1, loc2);
            return BuilderStatus.READY;
        } else {
            setupLandMarkerLabel(getGUI(), null, null);
            return BuilderStatus.NO_WORKAREA;
        }
    }

    private void setupLandMarkerLabel(InventoryGUI gui, Location loc1, Location loc2) {
        if (workArea == null) {
            gui.addLabel("Land Markers", 11, null, "Place two Land Markers", "in these slots, set", "to two opposite corners", "of the area to work.");
        } else {
            int v = workArea.volume();
            String s = v == 1 ? "" : "s";
            gui.addLabel("Land Markers", 11, null, "Work Area:",
                    MiscUtil.formatLocation(loc1), MiscUtil.formatLocation(loc2), v + " block" + s);
        }
    }

    @Override
    public boolean acceptsItemType(ItemStack stack) {
        // solid blocks, no special metadata
        return stack.getType().isSolid() && !stack.hasItemMeta();
    }

    @Override
    public boolean onSlotClick(HumanEntity player, int slot, ClickType click, ItemStack inSlot, ItemStack onCursor) {
        if (slot == LANDMARKER_SLOT_1 || slot == LANDMARKER_SLOT_2) {
            if (getStatus() != BuilderStatus.RUNNING) {
                if (onCursor.getType() != Material.AIR) {
                    LandMarker item = SensibleToolbox.getItemRegistry().fromItemStack(onCursor, LandMarker.class);
                    if (item != null) {
                        ItemStack stack = onCursor.clone();
                        stack.setAmount(1);
                        getGUI().getInventory().setItem(slot, stack);
                    }
                } else if (inSlot != null) {
                    getGUI().getInventory().setItem(slot, null);
                }
                setStatus(setupWorkArea());
            }
            return false; // we just put a copy of the land marker into the builder
        } else {
            return super.onSlotClick(player, slot, click, inSlot, onCursor);
        }
    }

    @Override
    public boolean onPlayerInventoryClick(HumanEntity player, int slot, ClickType click, ItemStack inSlot, ItemStack onCursor) {
        return true;
    }

    @Override
    public int onShiftClickInsert(HumanEntity player, int slot, ItemStack toInsert) {
        BaseSTBItem item = SensibleToolbox.getItemRegistry().fromItemStack(toInsert);

        if (item instanceof LandMarker && getStatus() != BuilderStatus.RUNNING) {
            if (((LandMarker) item).getMarkedLocation() != null) {
                insertLandMarker(toInsert);
            } else {
                STBUtil.complain((Player) player, "Land Marker doesn't have a location set!");
            }
            return 0;  // we just put a copy of the land marker into the builder
        } else {
            return super.onShiftClickInsert(player, slot, toInsert);
        }
    }

    private void insertLandMarker(ItemStack toInsert) {
        ItemStack stack = toInsert.clone();
        stack.setAmount(1);
        if (getInventoryItem(LANDMARKER_SLOT_1) == null) {
            setInventoryItem(LANDMARKER_SLOT_1, stack);
            setStatus(setupWorkArea());
        } else if (getInventoryItem(LANDMARKER_SLOT_2) == null) {
            setInventoryItem(LANDMARKER_SLOT_2, stack);
            setStatus(setupWorkArea());
        }
    }

    @Override
    public boolean onShiftClickExtract(HumanEntity player, int slot, ItemStack toExtract) {
        if ((slot == LANDMARKER_SLOT_1 || slot == LANDMARKER_SLOT_2) && getStatus() != BuilderStatus.RUNNING) {
            setInventoryItem(slot, null);
            setStatus(BuilderStatus.NO_WORKAREA);
            return false;
        } else {
            return super.onShiftClickExtract(player, slot, toExtract);
        }
    }

    @Override
    public boolean onClickOutside(HumanEntity player) {
        return false;
    }

    @Override
    public void onGUIOpened(HumanEntity player) {
    }

    @Override
    public void onGUIClosed(HumanEntity player) {
        if (player instanceof Player) {
            highlightWorkArea((Player) player);
        }
    }

    @SuppressWarnings("deprecation")
	private void highlightWorkArea(final Player p) {
        if (workArea != null) {
            final Block[] corners = workArea.corners();
            for (Block b : corners) {
                p.sendBlockChange(b.getLocation(), Material.STAINED_GLASS, DyeColor.LIME.getWoolData());
            }
            Bukkit.getScheduler().runTaskLater(getProviderPlugin(), new Runnable() {
                @Override
                public void run() {
                    if (p.isOnline()) {
                        for (Block b : corners) {
                            p.sendBlockChange(b.getLocation(), Material.STAINED_GLASS, DyeColor.GREEN.getWoolData());
                        }
                    }
                }
            }, 25L);
            Bukkit.getScheduler().runTaskLater(getProviderPlugin(), new Runnable() {
                @Override
                public void run() {
                    if (p.isOnline()) {
                        for (Block b : corners) {
                            p.sendBlockChange(b.getLocation(), b.getType(), b.getData());
                        }
                    }
                }
            }, 50L);
        }
    }

    @Override
    protected String[] getSignLabel(BlockFace face) {
        String[] label = super.getSignLabel(face);
        label[2] = "-( " + STBUtil.dyeColorToChatColor(getStatus().getColor()) + "⬤" + ChatColor.RESET + " )-";
        return label;
    }

    public void setLimit(boolean flag) {
		limit = flag;
	}

	public boolean isLimited() {
		return limit;
	}

	public enum AutoBuilderMode {
        CLEAR(-1);

        private final int yDirection;

        private AutoBuilderMode(int yDir) {
            this.yDirection = yDir;
        }

        public int getYDirection() {
            return yDirection;
        }
    }

    public enum BuilderStatus {
        READY(DyeColor.LIME, "Ready to Operate!"),
        NO_WORKAREA(DyeColor.YELLOW, "No work area has", "been defined yet"),
        NO_INVENTORY(DyeColor.RED, "Out of building material!", "Place more blocks in", "the inventory and", "press Start to resume"),
        NO_PERMISSION(DyeColor.RED, "Builder doesn't have", "building rights in", "this area"),
        TOO_NEAR(DyeColor.RED, "Auto Builder is inside", "the work area!"),
        TOO_FAR(DyeColor.RED, "Auto Builder is too far", "away from the work area!", "Place it " + MAX_DISTANCE + " blocks or less from", "the edge of the work area"),
        LM_WORLDS_DIFFERENT(DyeColor.RED, "Land Markers are", "from different worlds!"),
        RUNNING(DyeColor.LIGHT_BLUE, "Builder is running", "Press Start button to pause"),
        PAUSED(DyeColor.ORANGE, "Builder has been paused", "Press Start button to resume"),
        FINISHED(DyeColor.WHITE, "Builder has finished!", "Ready for next operation"),
    	LIMIT_REACHED(DyeColor.YELLOW, "The Server Limit " , "Is Reached");

        private final DyeColor color;
        private final String[] text;

        BuilderStatus(DyeColor color, String... label) {
            this.color = color;
            this.text = label;
        }

        public String[] getText() {
            return text;
        }

        public ItemStack makeTexture() {
            return STBUtil.makeColouredMaterial(Material.WOOL, color).toItemStack();
        }

        public DyeColor getColor() {
            return color;
        }
        public int getTickRate() {
            return 2;
        }

        public boolean resetBuildPosition() {
            // returning true causes the build position to be reset
            // when the Start button is pressed
            return this != PAUSED && this != NO_INVENTORY;
        }
    }

    public class AutoBuilderGadget extends CyclerGadget<AutoBuilderMode> {
        protected AutoBuilderGadget(InventoryGUI gui, int slot) {
            super(gui, slot, "Build Mode");
            add(AutoBuilderMode.CLEAR, ChatColor.YELLOW, STBUtil.makeColouredMaterial(Material.STAINED_GLASS, DyeColor.WHITE),
                    "Clear all blocks", "in the work area");
        }

        @Override
        protected boolean ownerOnly() {
            return false;
        }

        @Override
        protected void apply(BaseSTBItem stbItem, AutoBuilderMode newValue) {
            ((AutoBuilder) getGUI().getOwningBlock()).setBuildMode(newValue);
        }

        @Override
        public boolean isEnabled() {
            return getStatus() != BuilderStatus.RUNNING;
        }
    }
}
