package me.mrCookieSlime.sensibletoolbox;

/*
    This file is part of SensibleToolbox

    SensibleToolbox is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    SensibleToolbox is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with SensibleToolbox.  If not, see <http://www.gnu.org/licenses/>.
 */

import java.lang.reflect.InvocationTargetException;        
import java.lang.reflect.Method;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import com.comphenix.protocol.ProtocolLibrary;
import com.griefcraft.lwc.LWC;
import com.griefcraft.lwc.LWCPlugin;
import com.onarandombox.MultiverseCore.MultiverseCore;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.palmergames.bukkit.towny.Towny;
import net.coreprotect.CoreProtectAPI;
import net.coreprotect.CoreProtect;

import me.desht.dhutils.ConfigurationListener;
import me.desht.dhutils.ConfigurationManager;
import me.desht.dhutils.DHUtilsException;
import me.desht.dhutils.DHValidate;
import me.desht.dhutils.Debugger;
import me.desht.dhutils.ItemGlow;
import me.desht.dhutils.LogUtils;
import me.desht.dhutils.MessagePager;
import me.desht.dhutils.MiscUtil;
import me.desht.dhutils.commands.CommandManager;
import me.mrCookieSlime.CSCoreLibPlugin.PluginUtils;
import me.mrCookieSlime.CSCoreLibSetup.CSCoreLibLoader;
import me.mrCookieSlime.sensibletoolbox.api.AccessControl;
import me.mrCookieSlime.sensibletoolbox.api.FriendManager;
import me.mrCookieSlime.sensibletoolbox.api.RedstoneBehaviour;
import me.mrCookieSlime.sensibletoolbox.api.gui.InventoryGUI;
import me.mrCookieSlime.sensibletoolbox.api.recipes.RecipeUtil;
import me.mrCookieSlime.sensibletoolbox.api.util.BlockProtection;
import me.mrCookieSlime.sensibletoolbox.api.util.STBUtil;
import me.mrCookieSlime.sensibletoolbox.blocks.machines.AutoBuilder;
import me.mrCookieSlime.sensibletoolbox.commands.ChargeCommand;
import me.mrCookieSlime.sensibletoolbox.commands.DebugCommand;
import me.mrCookieSlime.sensibletoolbox.commands.ExamineCommand;
import me.mrCookieSlime.sensibletoolbox.commands.FriendCommand;
import me.mrCookieSlime.sensibletoolbox.commands.GetcfgCommand;
import me.mrCookieSlime.sensibletoolbox.commands.GiveCommand;
import me.mrCookieSlime.sensibletoolbox.commands.ParticleCommand;
import me.mrCookieSlime.sensibletoolbox.commands.RedrawCommand;
import me.mrCookieSlime.sensibletoolbox.commands.SaveCommand;
import me.mrCookieSlime.sensibletoolbox.commands.SetcfgCommand;
import me.mrCookieSlime.sensibletoolbox.commands.ShowCommand;
import me.mrCookieSlime.sensibletoolbox.commands.SoundCommand;
import me.mrCookieSlime.sensibletoolbox.commands.UnfriendCommand;
import me.mrCookieSlime.sensibletoolbox.commands.ValidateCommand;
import me.mrCookieSlime.sensibletoolbox.core.IDTracker;
import me.mrCookieSlime.sensibletoolbox.core.STBFriendManager;
import me.mrCookieSlime.sensibletoolbox.core.STBItemRegistry;
import me.mrCookieSlime.sensibletoolbox.core.energy.EnergyNetManager;
import me.mrCookieSlime.sensibletoolbox.core.gui.STBInventoryGUI;
import me.mrCookieSlime.sensibletoolbox.core.storage.LocationManager;
import me.mrCookieSlime.sensibletoolbox.items.LandMarker;
import me.mrCookieSlime.sensibletoolbox.items.energycells.FiftyKEnergyCell;
import me.mrCookieSlime.sensibletoolbox.items.energycells.TenKEnergyCell;
import me.mrCookieSlime.sensibletoolbox.listeners.AnvilListener;
import me.mrCookieSlime.sensibletoolbox.listeners.FurnaceListener;
import me.mrCookieSlime.sensibletoolbox.listeners.GeneralListener;
import me.mrCookieSlime.sensibletoolbox.listeners.PlayerUUIDTracker;
import me.mrCookieSlime.sensibletoolbox.listeners.WorldListener;

public class SensibleToolboxPlugin extends JavaPlugin implements ConfigurationListener {

    private static SensibleToolboxPlugin instance = null;
    private final CommandManager cmds = new CommandManager(this);
    private ConfigurationManager configManager;
    private boolean protocolLibEnabled = false;
    private PlayerUUIDTracker uuidTracker;
    private boolean inited = false;
    private boolean holographicDisplays = false;
    private BukkitTask energyTask = null;
    private LWC lwc = null;
    private STBItemRegistry itemRegistry;
    private STBFriendManager friendManager;
    private EnergyNetManager enetManager;
    private WorldGuardPlugin worldGuardPlugin = null;
    private CoreProtectAPI CoreProtectAPI = null;
    private CoreProtect CoreProtect = null;
    private Towny TownyPlugin = null;
    private BlockProtection blockProtection;
    private ConfigCache configCache;
    private MultiverseCore multiverseCore = null;
    private IDTracker scuRelayIDTracker;

    public static SensibleToolboxPlugin getInstance() {
        return instance;
    }
    
    public void registerItems() {
        final String CONFIG_NODE = "items_enabled";
        final String PERMISSION_NODE = "stb";

        itemRegistry.registerItem(new TenKEnergyCell(), this, CONFIG_NODE, PERMISSION_NODE);
        itemRegistry.registerItem(new FiftyKEnergyCell(), this, CONFIG_NODE, PERMISSION_NODE);
        itemRegistry.registerItem(new LandMarker(), this, CONFIG_NODE, PERMISSION_NODE);
        itemRegistry.registerItem(new AutoBuilder(), this, CONFIG_NODE, PERMISSION_NODE);
        
        }

    
    @Override
    public void onEnable() {
        CSCoreLibLoader loader = new CSCoreLibLoader(this);
        if (loader.load()) {
        	instance = this;

            LogUtils.init(this);
            
            PluginUtils utils = new PluginUtils(this);
            utils.setupUpdater(79884, getFile());
            utils.setupMetrics();
            
            configManager = new ConfigurationManager(this, this);

            configCache = new ConfigCache(this);
            configCache.processConfig();

            MiscUtil.init(this);
            MiscUtil.setColouredConsole(getConfig().getBoolean("coloured_console"));

            LogUtils.setLogLevel(getConfig().getString("log_level", "INFO"));

            Debugger.getInstance().setPrefix("[STB] ");
            Debugger.getInstance().setLevel(getConfig().getInt("debug_level"));
            if (getConfig().getInt("debug_level") > 0) Debugger.getInstance().setTarget(getServer().getConsoleSender());

            // try to hook other plugins
            holographicDisplays = getServer().getPluginManager().isPluginEnabled("HolographicDisplays");
            setupProtocolLib();
            setupLWC();
            setupWorldGuard();
            setupTowny();
            setupMultiverse();

            scuRelayIDTracker = new IDTracker(this, "scu_relay_id");

            blockProtection = new BlockProtection(this);

            STBInventoryGUI.buildStockTextures();

            itemRegistry = new STBItemRegistry();
            registerItems();

            friendManager = new STBFriendManager(this);
            enetManager = new EnergyNetManager(this);

            registerEventListeners();
            registerCommands();

            try {
                LocationManager.getManager().load();
            } catch (Exception e) {
                e.printStackTrace();
                setEnabled(false);
                return;
            }

            MessagePager.setPageCmd("/stb page [#|n|p]");
            MessagePager.setDefaultPageSize(getConfig().getInt("pager.lines", 0));

            // do all the recipe setup on a delayed task to ensure we pick up
            // custom recipes from any plugins that may have loaded after us
            Bukkit.getScheduler().runTask(this, new Runnable() {
                @Override
                public void run() {
                    RecipeUtil.findVanillaFurnaceMaterials();
                    RecipeUtil.setupRecipes();
                }
            });

            Bukkit.getScheduler().runTaskTimer(this, new Runnable() {
                @Override
                public void run() {
                    LocationManager.getManager().tick();
                }
            }, 1L, 1L);


            Bukkit.getScheduler().runTaskTimer(this, new Runnable() {
                @Override
                public void run() {
                    friendManager.save();
                }
            }, 60L, 300L);
        }
    }

    public void onDisable() {
        if (!inited) {
            return;
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            // Any open inventory GUI's must be closed -
            // if they stay open after server reload, event dispatch will probably not work,
            // allowing fake items to be removed from them - not a good thing
            InventoryGUI gui = STBInventoryGUI.getOpenGUI(p);
            if (gui != null) {
                gui.hide(p);
                p.closeInventory();
            }
        }
        LocationManager.getManager().save();
        LocationManager.getManager().shutdown();

        friendManager.save();

        Bukkit.getScheduler().cancelTasks(this);

        instance = null;
    }
    private void registerEventListeners() {
        PluginManager pm = this.getServer().getPluginManager();
        pm.registerEvents(new GeneralListener(this), this);
        pm.registerEvents(new FurnaceListener(this), this);
        pm.registerEvents(new WorldListener(this), this);
        pm.registerEvents(new AnvilListener(this), this);
        uuidTracker = new PlayerUUIDTracker(this);
        pm.registerEvents(uuidTracker, this);
        }
    

    private void setupWorldGuard() {
        Plugin plugin = getServer().getPluginManager().getPlugin("WorldGuard");

        if (plugin != null && plugin.isEnabled() && plugin instanceof WorldGuardPlugin) {
            Debugger.getInstance().debug("Hooked WorldGuard v" + plugin.getDescription().getVersion());
            worldGuardPlugin = (WorldGuardPlugin) plugin;
        }
    }
    public CoreProtectAPI getCoreProtect() {
    	Plugin plugin = getServer().getPluginManager().getPlugin("CoreProtect");
    	     
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
    private void setupTowny() {
        Plugin plugin = getServer().getPluginManager().getPlugin("Towny");

        if (plugin != null && plugin.isEnabled() && plugin instanceof Towny) {
            Debugger.getInstance().debug("Hooked Towny v" + plugin.getDescription().getVersion());
            TownyPlugin = (Towny) plugin;
        }
    }

    private void setupProtocolLib() {
        Plugin pLib = getServer().getPluginManager().getPlugin("ProtocolLib");
        if (pLib != null && pLib.isEnabled() && pLib instanceof ProtocolLibrary) {
            protocolLibEnabled = true;
            Debugger.getInstance().debug("Hooked ProtocolLib v" + pLib.getDescription().getVersion());
        }
        if (protocolLibEnabled) {
            if (getConfig().getBoolean("options.glowing_items"))ItemGlow.init(this);
        } 
        else {
            LogUtils.warning("ProtocolLib not detected - some functionality is reduced:");
            LogUtils.warning("  No glowing items, Reduced particle effects, Sound Muffler item disabled");
        }
    }

    private void setupMultiverse() {
        Plugin mvPlugin = getServer().getPluginManager().getPlugin("Multiverse-Core");
        if (mvPlugin != null && mvPlugin.isEnabled() && mvPlugin instanceof MultiverseCore) {
            multiverseCore = (MultiverseCore) mvPlugin;
            Debugger.getInstance().debug("Hooked Multiverse-Core v" + mvPlugin.getDescription().getVersion());
        }
    }

    private void setupLWC() {
        Plugin lwcPlugin = getServer().getPluginManager().getPlugin("LWC");
        if (lwcPlugin != null && lwcPlugin.isEnabled() && lwcPlugin instanceof LWCPlugin) {
            lwc = ((LWCPlugin) lwcPlugin).getLWC();
            Debugger.getInstance().debug("Hooked LWC v" + lwcPlugin.getDescription().getVersion());
        }
    }

    public boolean isProtocolLibEnabled() {
        return protocolLibEnabled;
    }

    public boolean isHolographicDisplaysEnabled() {
        return holographicDisplays;
    }

    private void registerCommands() {
        cmds.registerCommand(new SaveCommand());
        cmds.registerCommand(new GiveCommand());
        cmds.registerCommand(new ShowCommand());
        cmds.registerCommand(new ChargeCommand());
        cmds.registerCommand(new GetcfgCommand());
        cmds.registerCommand(new SetcfgCommand());
        cmds.registerCommand(new DebugCommand());
        cmds.registerCommand(new ParticleCommand());
        cmds.registerCommand(new SoundCommand());
        cmds.registerCommand(new ExamineCommand());
        cmds.registerCommand(new RedrawCommand());
        cmds.registerCommand(new FriendCommand());
        cmds.registerCommand(new UnfriendCommand());
        cmds.registerCommand(new ValidateCommand());
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        try {
            return cmds.dispatch(sender, command, label, args);
        } catch (DHUtilsException e) {
            MiscUtil.errorMessage(sender, e.getMessage());
            return true;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        return cmds.onTabComplete(sender, command, label, args);
    }

    @Override
    public Object onConfigurationValidate(ConfigurationManager configurationManager, String key, Object oldVal, Object newVal) {
        if (key.equals("save_interval")) {
            DHValidate.isTrue((Integer) newVal > 0, "save_interval must be > 0");
        } else if (key.equals("energy.tick_rate")) {
            DHValidate.isTrue((Integer) newVal > 0, "energy.tick_rate must be > 0");
        } else if (key.startsWith("gui.texture.")) {
            STBUtil.parseMaterialSpec(newVal.toString());
        } else if (key.equals("inventory_protection")) {
            getEnumValue(newVal.toString().toUpperCase(), BlockProtection.InvProtectionType.class);
        } else if (key.equals("block_protection")) {
            getEnumValue(newVal.toString().toUpperCase(), BlockProtection.BlockProtectionType.class);
        } else if (key.equals("default_access")) {
            getEnumValue(newVal.toString().toUpperCase(), AccessControl.class);
        } else if (key.equals("default_redstone")) {
            getEnumValue(newVal.toString().toUpperCase(), RedstoneBehaviour.class);
        }
        return newVal;
    }

    @SuppressWarnings({ "unchecked" })
	private <T> T getEnumValue(String value, Class<T> c) {
        try {
            Method m = c.getMethod("valueOf", String.class);
            //noinspection unchecked
            return (T) m.invoke(null, value);
        } catch (Exception e) {
            if (!(e instanceof InvocationTargetException) || !(e.getCause() instanceof IllegalArgumentException)) {
                e.printStackTrace();
                throw new DHUtilsException(e.getMessage());
            } else {
                throw new DHUtilsException("Unknown value: " + value);
            }
        }
    }

    @Override
    public void onConfigurationChanged(ConfigurationManager configurationManager, String key, Object oldVal, Object newVal) {
        if (key.equals("debug_level")) {
            Debugger dbg = Debugger.getInstance();
            dbg.setLevel((Integer) newVal);
            if (dbg.getLevel() > 0) {
                dbg.setTarget(getServer().getConsoleSender());
            } else {
                dbg.setTarget(null);
            }
        } else if (key.equals("save_interval")) {
            LocationManager.getManager().setSaveInterval((Integer) newVal);
        } else if (key.equals("energy.tick_rate")) {
            scheduleEnergyNetTicker();
        } else if (key.startsWith("gui.texture.")) {
            STBInventoryGUI.buildStockTextures();
        } else if (key.equals("inventory_protection")) {
            blockProtection.setInvProtectionType(BlockProtection.InvProtectionType.valueOf(newVal.toString().toUpperCase()));
        } else if (key.equals("block_protection")) {
            blockProtection.setBlockProtectionType(BlockProtection.BlockProtectionType.valueOf(newVal.toString().toUpperCase()));
        } else if (key.equals("default_access")) {
            getConfigCache().setDefaultAccess(AccessControl.valueOf(newVal.toString().toUpperCase()));
        } else if (key.equals("default_redstone")) {
            getConfigCache().setDefaultRedstone(RedstoneBehaviour.valueOf(newVal.toString().toUpperCase()));
        } else if (key.equals("particle_effects")) {
            getConfigCache().setParticleLevel((Integer) newVal);
        } else if (key.equals("noisy_machines")) {
            getConfigCache().setNoisyMachines((Boolean) newVal);
        } else if (key.equals("creative_ender_access")) {
            getConfigCache().setCreativeEnderAccess((Boolean) newVal);
        }
    }

    private void scheduleEnergyNetTicker() {
        if (energyTask != null) energyTask.cancel();
        enetManager.setTickRate(getConfig().getLong("energy.tick_rate", EnergyNetManager.DEFAULT_TICK_RATE));
        energyTask = Bukkit.getScheduler().runTaskTimer(this, new Runnable() {
            @Override
            public void run() {
                enetManager.tick();
            }
        }, 1L, enetManager.getTickRate());
    }

    public ConfigurationManager getConfigManager() 			{			return configManager;														}
    	{																}
    public STBItemRegistry getItemRegistry() 				{			return itemRegistry;														}
    public FriendManager getFriendManager() 				{			return friendManager;														}
    public EnergyNetManager getEnergyNetManager() 			{			return enetManager;															}
    public boolean isWorldGuardAvailable() 					{			return worldGuardPlugin != null && worldGuardPlugin.isEnabled();			}
    public boolean isCoreProtectAvailable() 					{			return CoreProtect != null && CoreProtect.isEnabled();			}
    public boolean isCoreProtectAPIAvailable() 					{			return CoreProtectAPI != null && CoreProtectAPI.isEnabled();			}
    public boolean isTownyAvailable() 				{			return TownyPlugin != null && TownyPlugin.isEnabled(); 	}
    public BlockProtection getBlockProtection() 			{			return blockProtection;														}
    public ConfigCache getConfigCache() 					{			return configCache;															}
    public MultiverseCore getMultiverseCore() 				{			return multiverseCore;														}
	public IDTracker getScuRelayIDTracker() 				{			return scuRelayIDTracker;													}
    public LWC getLWC()									 	{			return lwc;																	}
     	{														}
    public PlayerUUIDTracker getUuidTracker() 				{			return uuidTracker;															}

	public boolean isGlowingEnabled() {
		return isProtocolLibEnabled() && getConfig().getBoolean("options.glowing_items");
	}
}
