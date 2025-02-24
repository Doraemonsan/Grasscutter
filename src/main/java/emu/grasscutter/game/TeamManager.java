package emu.grasscutter.game;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import dev.morphia.annotations.Transient;
import emu.grasscutter.GenshinConstants;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.def.AvatarSkillDepotData;
import emu.grasscutter.game.avatar.GenshinAvatar;
import emu.grasscutter.game.entity.EntityAvatar;
import emu.grasscutter.game.entity.EntityGadget;
import emu.grasscutter.game.props.ElementType;
import emu.grasscutter.game.props.EnterReason;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.net.packet.GenshinPacket;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.EnterTypeOuterClass.EnterType;
import emu.grasscutter.net.proto.MotionStateOuterClass.MotionState;
import emu.grasscutter.server.packet.send.PacketAvatarDieAnimationEndRsp;
import emu.grasscutter.server.packet.send.PacketAvatarFightPropUpdateNotify;
import emu.grasscutter.server.packet.send.PacketAvatarLifeStateChangeNotify;
import emu.grasscutter.server.packet.send.PacketAvatarTeamUpdateNotify;
import emu.grasscutter.server.packet.send.PacketChangeAvatarRsp;
import emu.grasscutter.server.packet.send.PacketChangeMpTeamAvatarRsp;
import emu.grasscutter.server.packet.send.PacketChangeTeamNameRsp;
import emu.grasscutter.server.packet.send.PacketChooseCurAvatarTeamRsp;
import emu.grasscutter.server.packet.send.PacketPlayerEnterSceneNotify;
import emu.grasscutter.server.packet.send.PacketSceneTeamUpdateNotify;
import emu.grasscutter.server.packet.send.PacketSetUpAvatarTeamRsp;
import emu.grasscutter.server.packet.send.PacketWorldPlayerDieNotify;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;

public class TeamManager {
	@Transient private GenshinPlayer player;
	
	private Map<Integer, TeamInfo> teams;
	private int currentTeamIndex;
	private int currentCharacterIndex;
	
	@Transient private TeamInfo mpTeam;
	@Transient private int entityId;
	@Transient private final List<EntityAvatar> avatars;
	@Transient private final Set<EntityGadget> gadgets;
	@Transient private final IntSet teamResonances;
	@Transient private final IntSet teamResonancesConfig;
	
	public TeamManager() {
		this.mpTeam = new TeamInfo();
		this.avatars = new ArrayList<>();
		this.gadgets = new HashSet<>();
		this.teamResonances = new IntOpenHashSet();
		this.teamResonancesConfig = new IntOpenHashSet();
	}
	
	public TeamManager(GenshinPlayer player) {
		this();
		this.player = player;
		
		this.teams = new HashMap<>();
		this.currentTeamIndex = 1;
		for (int i = 1; i <= GenshinConstants.MAX_TEAMS; i++) {
			this.teams.put(i, new TeamInfo());
		}
	}
	
	public GenshinPlayer getPlayer() {
		return player;
	}
	
	public World getWorld() {
		return player.getWorld();
	}

	public void setPlayer(GenshinPlayer player) {
		this.player = player;
	}
	
	public Map<Integer, TeamInfo> getTeams() {
		return this.teams;
	}

	public TeamInfo getMpTeam() {
		return mpTeam;
	}

	public void setMpTeam(TeamInfo mpTeam) {
		this.mpTeam = mpTeam;
	}

	public int getCurrentTeamId() {
		// Starts from 1
		return currentTeamIndex;
	}

	private void setCurrentTeamId(int currentTeamIndex) {
		this.currentTeamIndex = currentTeamIndex;
	}

	public int getCurrentCharacterIndex() {
		return currentCharacterIndex;
	}

	public void setCurrentCharacterIndex(int currentCharacterIndex) {
		this.currentCharacterIndex = currentCharacterIndex;
	}

	public long getCurrentCharacterGuid() {
		return getCurrentAvatarEntity().getAvatar().getGuid();
	}
	
	public TeamInfo getCurrentTeamInfo() {
		if (this.getPlayer().isInMultiplayer()) {
			return this.getMpTeam();
		}
		return this.getTeams().get(this.currentTeamIndex);
	}
	
	public TeamInfo getCurrentSinglePlayerTeamInfo() {
		return this.getTeams().get(this.currentTeamIndex);
	}

	public int getEntityId() {
		return entityId;
	}

	public void setEntityId(int entityId) {
		this.entityId = entityId;
	}

	public Set<EntityGadget> getGadgets() {
		return gadgets;
	}

	public IntSet getTeamResonances() {
		return teamResonances;
	}

	public IntSet getTeamResonancesConfig() {
		return teamResonancesConfig;
	}

	public List<EntityAvatar> getActiveTeam() {
		return avatars;
	}
	
	public EntityAvatar getCurrentAvatarEntity() {
		return getActiveTeam().get(currentCharacterIndex);
	}
	
	public boolean isSpawned() {
		return getPlayer().getScene() != null && getPlayer().getScene().getEntities().containsKey(getCurrentAvatarEntity().getId());
	}
	
	public int getMaxTeamSize() {
		if (getPlayer().isInMultiplayer()) {
			int max = Grasscutter.getConfig().getServerOptions().MaxAvatarsInTeamMultiplayer;
			if (getPlayer().getWorld().getHost() == this.getPlayer()) {
				return Math.max(1, (int) Math.ceil(max / (double) getWorld().getPlayerCount()));
			}
			return Math.max(1, (int) Math.floor(max / (double) getWorld().getPlayerCount()));
		}
		return Grasscutter.getConfig().getServerOptions().MaxAvatarsInTeam;
	}
	
	// Methods
	
	private void updateTeamResonances() {
		Int2IntOpenHashMap map = new Int2IntOpenHashMap();
		
		this.getTeamResonances().clear();
		this.getTeamResonancesConfig().clear();
		
		for (EntityAvatar entity : getActiveTeam()) {
			AvatarSkillDepotData skillData = entity.getAvatar().getAvatarData().getSkillDepot();
			if (skillData != null) {
				map.addTo(skillData.getElementType().getValue(), 1);
			}
		}
		
		for (Int2IntMap.Entry e : map.int2IntEntrySet()) {
			if (e.getIntValue() >= 2) {
				ElementType element = ElementType.getTypeByValue(e.getIntKey());
				if (element.getTeamResonanceId() != 0) {
					this.getTeamResonances().add(element.getTeamResonanceId());
					this.getTeamResonancesConfig().add(element.getConfigHash());
				}
			}
		}
		
		// No resonances
		if (this.getTeamResonances().size() == 0) {
			this.getTeamResonances().add(ElementType.Default.getTeamResonanceId());
			this.getTeamResonancesConfig().add(ElementType.Default.getTeamResonanceId());
		}
	}
	
	public void updateTeamEntities(GenshinPacket responsePacket) {
		// Sanity check - Should never happen
		if (this.getCurrentTeamInfo().getAvatars().size() <= 0) {
			return;
		}
		
		// If current team has changed
		EntityAvatar currentEntity = this.getCurrentAvatarEntity();
		Int2ObjectMap<EntityAvatar> existingAvatars = new Int2ObjectOpenHashMap<>();
		int prevSelectedAvatarIndex = -1;
		
		for (EntityAvatar entity : getActiveTeam()) {
			existingAvatars.put(entity.getAvatar().getAvatarId(), entity);
		}
		
		// Clear active team entity list
		this.getActiveTeam().clear();
		
		// Add back entities into team
		for (int i = 0; i < this.getCurrentTeamInfo().getAvatars().size(); i++) {
			int avatarId = this.getCurrentTeamInfo().getAvatars().get(i);
			EntityAvatar entity = null;
			
			if (existingAvatars.containsKey(avatarId)) {
				entity = existingAvatars.get(avatarId);
				existingAvatars.remove(avatarId);
				if (entity == currentEntity) {
					prevSelectedAvatarIndex = i;
				}
			} else {
				entity = new EntityAvatar(getPlayer().getScene(), getPlayer().getAvatars().getAvatarById(avatarId));
			}
			
			this.getActiveTeam().add(entity);
		}
		
		// Unload removed entities
		for (EntityAvatar entity : existingAvatars.values()) {
			getPlayer().getScene().removeEntity(entity);
			entity.getAvatar().save();
		}
		
		// Set new selected character index
		if (prevSelectedAvatarIndex == -1) {
			// Previous selected avatar is not in the same spot, we will select the current one in the prev slot
			prevSelectedAvatarIndex = Math.min(this.currentCharacterIndex, this.getActiveTeam().size() - 1);
		}
		this.currentCharacterIndex = prevSelectedAvatarIndex;
		
		// Update team resonances
		updateTeamResonances();
		
		// Packets
		getPlayer().getWorld().broadcastPacket(new PacketSceneTeamUpdateNotify(getPlayer()));
		
		// Run callback
		if (responsePacket != null) {
			getPlayer().sendPacket(responsePacket);
		}

		// Check if character changed
		if (currentEntity != getCurrentAvatarEntity()) {
			// Remove and Add
			getPlayer().getScene().replaceEntity(currentEntity, getCurrentAvatarEntity());
		}
	}
	
	public synchronized void setupAvatarTeam(int teamId, List<Long> list) {
		// Sanity checks
		if (list.size() == 0 || list.size() > getMaxTeamSize() || getPlayer().isInMultiplayer()) {
			return;
		}
		
		// Get team
		TeamInfo teamInfo = this.getTeams().get(teamId);
		if (teamInfo == null) {
			return;
		}
		
		// Set team data
		LinkedHashSet<GenshinAvatar> newTeam = new LinkedHashSet<>();
		for (int i = 0; i < list.size(); i++) {
			GenshinAvatar avatar = getPlayer().getAvatars().getAvatarByGuid(list.get(i));
			if (avatar == null || newTeam.contains(avatar)) {
				// Should never happen
				return;
			}
			newTeam.add(avatar);
		}
		
		// Clear current team info and add avatars from our new team
		teamInfo.getAvatars().clear();
		for (GenshinAvatar avatar : newTeam) {
			teamInfo.addAvatar(avatar);
		}
		
		// Update packet
		getPlayer().sendPacket(new PacketAvatarTeamUpdateNotify(getPlayer()));
		
		// Update entites
		if (teamId == this.getCurrentTeamId()) {
			this.updateTeamEntities(new PacketSetUpAvatarTeamRsp(getPlayer(), teamId, teamInfo));
		} else {
			getPlayer().sendPacket(new PacketSetUpAvatarTeamRsp(getPlayer(), teamId, teamInfo));
		}
	}
	
	public void setupMpTeam(List<Long> list) {
		// Sanity checks
		if (list.size() == 0 || list.size() > getMaxTeamSize() || !getPlayer().isInMultiplayer()) {
			return;
		}

		TeamInfo teamInfo = this.getMpTeam();
		
		// Set team data
		LinkedHashSet<GenshinAvatar> newTeam = new LinkedHashSet<>();
		for (int i = 0; i < list.size(); i++) {
			GenshinAvatar avatar = getPlayer().getAvatars().getAvatarByGuid(list.get(i));
			if (avatar == null || newTeam.contains(avatar)) {
				// Should never happen
				return;
			}
			newTeam.add(avatar);
		}
		
		// Clear current team info and add avatars from our new team
		teamInfo.getAvatars().clear();
		for (GenshinAvatar avatar : newTeam) {
			teamInfo.addAvatar(avatar);
		}
		
		// Packet
		this.updateTeamEntities(new PacketChangeMpTeamAvatarRsp(getPlayer(), teamInfo));
	}
	
	public synchronized void setCurrentTeam(int teamId) {
		// 
		if (getPlayer().isInMultiplayer()) {
			return;
		}
		
		// Get team
		TeamInfo teamInfo = this.getTeams().get(teamId);
		if (teamInfo == null || teamInfo.getAvatars().size() == 0) {
			return;
		}
		
		// Set
		this.setCurrentTeamId(teamId);
		this.updateTeamEntities(new PacketChooseCurAvatarTeamRsp(teamId));
	}

	public synchronized void setTeamName(int teamId, String teamName) {
		// Get team
		TeamInfo teamInfo = this.getTeams().get(teamId);
		if (teamInfo == null) {
			return;
		}
		
		teamInfo.setName(teamName);
		
		// Packet
		getPlayer().sendPacket(new PacketChangeTeamNameRsp(teamId, teamName));
	}

	public synchronized void changeAvatar(long guid) {
		EntityAvatar oldEntity = this.getCurrentAvatarEntity();
		
		if (guid == oldEntity.getAvatar().getGuid()) {
			return;
		}

		EntityAvatar newEntity = null;
		int index = -1;
		for (int i = 0; i < getActiveTeam().size(); i++) {
			if (guid == getActiveTeam().get(i).getAvatar().getGuid()) {
				index = i;
				newEntity = getActiveTeam().get(i);
			}
		}
		
		if (index < 0 || newEntity == oldEntity) {
			return;
		}
		
		// Set index
		this.setCurrentCharacterIndex(index);
		
		// Old entity motion state
		oldEntity.setMotionState(MotionState.MotionStandby);

		// Remove and Add
		getPlayer().getScene().replaceEntity(oldEntity, newEntity);
		getPlayer().sendPacket(new PacketChangeAvatarRsp(guid));
	}
	
	public void onAvatarDie(long dieGuid) {
		EntityAvatar deadAvatar = this.getCurrentAvatarEntity();
		
		if (deadAvatar.isAlive() || deadAvatar.getId() != dieGuid) {
			return;
		}
		
		// Replacement avatar
		EntityAvatar replacement = null;
		int replaceIndex = -1;
		
		for (int i = 0; i < this.getActiveTeam().size(); i++) {
			EntityAvatar entity = this.getActiveTeam().get(i);
			if (entity.isAlive()) {
				replaceIndex = i;
				replacement = entity;
				break;
			}
		}
		
		if (replacement == null) {
			// No more living team members...
			getPlayer().sendPacket(new PacketWorldPlayerDieNotify(deadAvatar.getKilledType(), deadAvatar.getKilledBy()));
		} else {
			// Set index and spawn replacement member
			this.setCurrentCharacterIndex(replaceIndex);
			getPlayer().getScene().addEntity(replacement);
		}

		// Response packet
		getPlayer().sendPacket(new PacketAvatarDieAnimationEndRsp(deadAvatar.getId(), 0));
	}
	
	public boolean reviveAvatar(GenshinAvatar avatar) {
		for (EntityAvatar entity : getActiveTeam()) {
			if (entity.getAvatar() == avatar) {
				if (entity.isAlive()) {
					return false;
				}
				
				entity.setFightProperty(
						FightProperty.FIGHT_PROP_CUR_HP, 
						entity.getFightProperty(FightProperty.FIGHT_PROP_MAX_HP) * .1f
				);
				getPlayer().sendPacket(new PacketAvatarFightPropUpdateNotify(entity.getAvatar(), FightProperty.FIGHT_PROP_CUR_HP));
				getPlayer().sendPacket(new PacketAvatarLifeStateChangeNotify(entity.getAvatar()));
				return true;
			}
		}
		
		return false;
	}
	
	public void respawnTeam() {
		// Make sure all team members are dead
		for (EntityAvatar entity : getActiveTeam()) {
			if (entity.isAlive()) {
				return;
			}
		}
		
		// Revive all team members
		for (EntityAvatar entity : getActiveTeam()) {
			entity.setFightProperty(
					FightProperty.FIGHT_PROP_CUR_HP, 
					entity.getFightProperty(FightProperty.FIGHT_PROP_MAX_HP) * .4f
			);
			getPlayer().sendPacket(new PacketAvatarFightPropUpdateNotify(entity.getAvatar(), FightProperty.FIGHT_PROP_CUR_HP));
			getPlayer().sendPacket(new PacketAvatarLifeStateChangeNotify(entity.getAvatar()));
		}
		
		// Teleport player
		getPlayer().sendPacket(new PacketPlayerEnterSceneNotify(getPlayer(), EnterType.EnterSelf, EnterReason.Revival, 3, GenshinConstants.START_POSITION));
		
		// Set player position
		player.setSceneId(3);
		player.getPos().set(GenshinConstants.START_POSITION);

		// Packets
		getPlayer().sendPacket(new GenshinPacket(PacketOpcodes.WorldPlayerReviveRsp));
	}

	public void saveAvatars() {
		// Save all avatars from active team
		for (EntityAvatar entity : getActiveTeam()) {
			entity.getAvatar().save();
		}
	}
}
