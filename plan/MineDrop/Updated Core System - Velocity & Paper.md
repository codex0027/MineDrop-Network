# Core System - Velocity 

# Core System - Velocity 

Plugins: 

minedrop-proxy-core (minedrop-network) minedrop-auth minedrop-chat minedrop-security minedrop-maintenance 

###### VPS 

##### Velocity (Proxy) 

### Fallback Lobby —————— Lobby Public Server 

Private Server max. 32 Players max. 4 Teams with 8 Players 

Private Server | Private Server Private Server | Private Server Private Server | Private Server Private Server | Private Server 

# proxy-core 

## **Responsibilities** 

#### **Session Management** 

Tracks: 

- online players 

- active sessions 

- reconnects 

- server transfers 

#### **Player Cache** 

Stores: 

- coins 

- clan 

- permissions 

- prestige data 

- active boosts 

- protection status 

in memory for fast access. 

#### **Redis Connection** 

Handles: 

- Pub/Sub 

- cross-server communication 

- distributed caching 

#### **Backend API Connection** 

Communicates with: 

- auth service 

- economy service 

- analytics service 

- cloud service 

#### **Global Event System** 

Example: 

PLAYER_JOIN PLAYER_QUIT CLAN_CREATED DESTROYER_SPAWN 

#### **Token Management** 

Stores: 

- service tokens 

- authentication state 

- backend permissions 

#### **Crash Recovery** 

Features: 

- reconnect handling 

- server crash fallback 

- automatic player rerouting 

#### **Metrics** 

Tracks: 

- online count 

- transfer speed 

- proxy TPS 

- packet throughput 

(core-network) 

#### **Handles the entire network structure** 

## **Features** 

#### **Server Registry** 

Tracks: 

- all servers 

- status 

- regions 

- load 

- capacity 

#### **Dynamic Server Discovery** 

Automatically detects: 

public-1 public-2 clan-india clan-germany 

#### **Region System** 

Supports: 

- EU 

- NA 

- ASIA 

later for scalability. 

#### **Server Routing** 

Determines: 

- best lobby 

- best public world 

- clan server location 

#### **Smart Routing** 

Example: 

EU player → joins EU instance 

#### **Fallback Handling** 

If server crashes: 

player automatically moved to safe lobby 

# core-auth 

## **minedrop-auth** 

#### **Authentication & Account Security** 

## **Features** 

#### **Session Authentication** 

Every server must authenticate. 

#### **Service Tokens** 

Each server gets: 

service-id: service-secret: 

#### **Player Authentication** 

Tracks: 

- IP history 

- devices 

- sessions 

- alt accounts 

#### **2FA Support** 

For: 

- staff 

- admins 

- dashboard logins 

#### **Security Flags** 

Detects: 

- suspicious joins 

- VPN usage 

- rapid reconnects 

- token abuse 

#### **Login Queue Protection** 

Prevents: 

- bot joins 

- join spam 

- attacks 

core-chat 

## **minedrop-chat** 

#### **Global communication system** 

## **Features** 

#### **Global Chat** 

Cross-server. 

#### **(Translation System)** 

Possible future feature: German player ↔ English player 

#### **Anti-Spam** 

#### **Slowmode** 

#### **Chat Filtering** 

core-security 

## **minedrop-security** 

#### **Enterprise security layer** 

VERY important. 

## **Features** 

#### **Packet Validation** 

**Anti-Exploit** 

#### **Anti-Bot** 

#### **Anti-VPN** 

#### **Alt Detection** 

**Rate Limits** 

#### **Economy Security** 

Detects: 

- suspicious transfers 

- duping 

- bot farming 

#### **Session Protection** 

Prevents: 

- token hijacking 

- session spoofing 

#### **Machine Fingerprinting** 

Detects: 

- alt farms 

- multi-account abuse 

# core-maintenance 

## **minedrop-maintenance** 

#### **Network control system** 

## **Features** 

#### **Maintenance Mode** 

#### **Scheduled Restarts** 

#### **Whitelist Mode** 

**Staff Bypass** 

#### **Restart Warnings** 

Example: 

Server restarting in 5 minutes 

#### **Emergency Shutdowns** 

Can instantly: 

- lock network 

- freeze economy 

- disable auctions 

System - Paper 

###### Lobby Plugins 

- FancyNPCs 

- TAB 

- ItemsAdder 

- Vault 

- - Luckperms 

- Worldedit 

- MythicMobs 

- ModelEngine 

- MDN-Encryption 

- MDN-Economy 

- MDN-Moderation 

- MDN-Clans 

- MDN-Sync 

- MDN-Data-Sync 

- MDN-Discord-Sync 

- MDN-Friends 

- MDN-System 

###### Subserver (SAM) 

- ItemsAdder 

- ModelEngine 

- MythicMobs 

- WorldEdit 

- TAB 

- Luckperms 

- Vault 

- FancyNPCs 

- FancyHolograms 

- MDN-Encryption 

- MDN-Economy 

- MDN-Moderation 

- MDN-Clans 

- MDN-Ingame-Shop 

- MDN-SAM-Mechanic 

- - MDN-Sync 

- MDN-Data-Sync 

- - MDN-Discord-Sync 

- MDN-Friends 

- MDN-System 

# third-party plugins 

Fancy NPCs 

- <u>FancyNpcs Minecraft Plugin</u> 

<u>GitHub - FancyMcPlugins/FancyNpcs: FancyNpcs is a simple, lightweight and fast npc plugin</u> · <u>using packets GitHub</u> 

Fancy Holograms 

- <u>FancyHolograms Minecraft Plugin</u> 

<u>GitHub - FancyMcPlugins/FancyHolograms: FancyHolograms is a simple, lightweight and fast</u> · <u>hologram plugin using display entities GitHub</u> 

TAB 

<u>TAB - Minecraft Plugin</u> 

<u>GitHub - NEZNAMY/TAB: "That" TAB plugin. · GitHub</u> 

Luckperms 

<u>LuckPerms</u> 

Vault 

- <u>Vault | SpigotMC High Performance Minecraft Software</u> 

WorldEdit 

<u>WorldEdit - Minecraft Plugin</u> 

ItemsAdder 

_Jupitan owns the license_ 

MythicMobs 

_Jupitan owns the license_ 

ModelEngine 

_Jupitan owns the license_ 

# MDN-Economy 

## **MDN Economy & Auction House System** 

#### **Overview** 

The **MDN Economy & Auction House System** is a core gameplay module for the network. It provides a **global currency system** and a **player-driven marketplace** for trading items. 

It is fully server-wide and synchronized across all network servers. 

## **Economy System** 

#### **Concept** 

A global money system where every player has one account that is shared across all servers. 

#### **Features** 

- Global player balances (MySQL synchronized) 

- Supports integration with ItemsAdder 

- Supports custom entity models via ModelEngine 

- Compatible with mob system integration using MythicMobs 

- Vault support 

- Money transfers between players 

- Admin control over player balances 

- Transaction logging 

- Async database handling (no lag) 

- Offline support for transactions 

#### **Commands** 

###### **Player Commands** 

- /money - Show your balance 

- /pay <player> <amount> - Send money to another player` 

- /balancetop - Show richest 10 players 

###### **Admin Commands** 

- /eco add <player> <amount> 

- /eco take <player> <amount> 

- /eco set <player> <amount> 

- /eco reset <player> <amount> 

- /ecosys admin reload - reload economy plugin 

- /ecosys admin freeze - freezes all money interactions (Eco & AH) 

- /ecosys admin unfreeze - unfreezes all money interactions (Eco & AH) 

#### **Permissions** 

- mdn.eco.use - use economy commands 

- mdn.eco.pay - allow sending money 

- mdn.eco.balance - view balances 

- mdn.eco.admin - full economy control 

- mdn.ecosys.admin 

#### **Starting Balance** 

Every new player who joins the server for the first time automatically receives a starting balance of **500 Coins** . 

This starting amount is handled by the MDN Economy System and is only given once per player (first join). 

It ensures that new players can immediately use basic features such as shops, trading, or other economy-related systems without needing to earn money first. 

## **Auction House System** 

#### **Concept** 

A global marketplace where players can sell and buy items using the economy system. 

The Auction House is fully **MySQL-based and synchronized across multiple servers** , meaning: 

- All listings are shared network-wide 

- Items can be listed on one server and bought on another 

- Every update (sell, buy, cancel) is instantly synced across all servers 

#### **Features** 

- Global item marketplace 

- Supports integration with ItemsAdder 

- Supports custom entity models via ModelEngine 

- Compatible with mob system integration using MythicMobs 

- MySQL multi-server synchronization (all entries shared network-wide) 

- Real-time sync of listings across all servers 

- Sell items via GUI or command 

- Instant purchase system 

- Search and filter system 

- Listing fees (optional economy sink) 

- Sales history tracking 

- GUI-based interface 

- Anti-duplication protection 

- Fully async database system 

###### **GUI Interaction** 

- Left-click on an item → Purchase the item immediately (if you have enough money) 

- Right-click on your own listed item → Remove it from the Auction House and get it back into your inventory 

#### **Commands** 

###### **Player Commands** 

- /ah - Open Auction House 

- /ah sell <price> - List item for sale (The item must be in the player hand) 

- /ah cancel - Cancel your listing 

- /ah search <item> - Search items 

- /ah history - View your sales history 

#### **Permissions** 

- mdn.ah.use 

- mdn.ah.sell 

- mdn.ah.cancel 

- mdn.ah.admin 

## **Database** 

- MySQL required for both systems 

- Economy + Auction House fully stored in database 

- Auction House entries are **synchronized across all servers in real time** 

- Stores: 

   - Player balances 

   - Auction listings (global + synced) 

   - Transaction history 

- Async operations (no server lag) 

- Multi-server network support 

# MDN-Friends 

## **MDN Friend System** 

#### **Overview** 

The **MDN Friend System** allows players to add friends, manage social connections, and interact across the entire network. 

It is fully **MySQL-based and synchronized across all servers** , meaning friend data is shared network-wide. 

#### **Features** 

- Add / remove friends 

- Accept / decline friend requests 

- Online status of friends 

- Cross-server synchronization (MySQL) 

- Friend list management (GUI-based) 

- Private messaging support (optional integration) 

- Join notifications (friend joins/leaves server) 

- Block system (prevent interactions from specific players) 

- Clean storage system with async database handling 

#### **GUI Interaction** 

- **Left-click on a player in the friend list** → Open profile / interaction menu 

- **Right-click on a friend** → Remove friend 

- **Click on a friend request** → Accept / decline request 

#### **Commands** 

###### **Player Commands** 

- /friend add <player> - Send friend request 

- /friend accept <player> - Accept request 

- /friend deny <player> - Deny request 

- /friend remove <player> - Remove friend 

- /friend list - Open friend list GUI 

- /friend requests - View incoming requests 

- /friend team requests block - blocks all requests related to a team member 

- /friend team requests allow - allows all requests related to a team member 

(Only team members can add other players outside the team, provided they have enabled /friend team requests block ) 

#### **Permissions** 

- mdn.friend.use - basic friend system usage 

- mdn.friend.request - send friend requests 

- mdn.friend.admin – admin control over friend data 

- mdn.friend.team – team control over friend requests 

#### **Database (MySQL)** 

The system is fully synchronized across all servers. 

Stored data: 

- Friend relationships 

- Pending friend requests 

- Player social data 

All changes are: 

- Async processed (no lag) 

- Instantly synced across servers 

- Conflict-safe (no duplicate requests) 

# MDN-System 

## **MDN System** 

#### **Overview** 

The MDN System also includes a set of **standard server commands** that are available across the network. 

These commands are basic features provided by MDN-Core or connected modules and are used for general server navigation, information, and player interaction. 

#### **Standard Commands** 

###### **These commands exist by default in the MDN network (if enabled in the system configuration):** 

- **/website > Shows the official server website** 

- **/store > Opens the online shop / donation store** 

- **/vote > Shows voting links and rewards** 

- **/discord > Give the player an invite link to the Discord server** 

- **/help > Displays general help information** 

- **/rules > Shows server rules** 

- **/spawn > Teleports player to spawn** 

- **/hub > Sends player to the main lobby** 

- **/lobby > Sends player to the main lobby** 

#### **Command Availability** 

All standard commands: 

- Must be registered in MDN-Core or a module 

- Can be enabled or disabled in the configuration 

- Can be restricted by permissions 

- Are globally available only if activated 

Example: 

commands: website: true store: true vote: true discord: true 

freemoney: false 

#### **How It Works** 

If a command is enabled: 

➡ Players can use it normally 

➡ It is visible and functional 

If a command is disabled: 

➡ It does not execute 

➡ It is treated as non-existent 

#### **Purpose of Standard Commands** 

###### **These commands are meant to:** 

- Give players quick access to important links 

- Improve navigation on the server 

- Provide basic server functionality 

- Keep the network consistent and user-friendly 

# MDN-Clan 

## **MDN Clan System** 

#### **Overview** 

The **MDN Clan System** is a network-wide team system that allows players to create, join, and manage clans. 

It is fully **MySQL-based and synchronized across all servers** , so all clan data (members, invites, settings) is shared in real time across the network. 

#### **Core Concept** 

Players can: 

- Create clans 

- Join clans via invite or request 

- Manage clan members 

- Browse clans in a GUI 

###### **Limits (default)** 

- Each clan can have **max. 8 members** 

- Limits can be changed via permissions 

#### **Clan Member System** 

- Each clan supports up to **8 players total** 

- Roles: 

   - Owner 

   - Members 

###### **Owner abilities:** 

- Remove up to **7 members** 

- Manage invites 

- Manage clan settings 

- Promote / control structure (if extended later) 

#### **GUI System** 

###### **Main Clan GUI** 

Players can open a GUI showing: 

- Up to **3 featured / available clans** 

- Clan name 

- Member count (e.g. 5/8) 

- Owner name 

- Join button 

- Info button 

###### **Clan Info GUI** 

Shows: 

- Clan name 

- Owner 

- Member list 

- Current online members 

- Join / request button (if not member) 

###### **Member Management GUI (Owner only)** 

- Full list of all members 

- Kick button per member (except owner) 

- Invite management 

- Clan settings access 

#### **GUI Actions** 

- **Left-click clan** → Open clan info / send join request 

- **Right-click clan** → Ignore clan (hide from suggestions) 

- **Block option** → Prevent receiving invites from this clan 

- **Owner click on member** → Remove member from clan 

#### **Commands** 

###### **Player Commands** 

- /clan – Open main clan GUI 

- /clan create <name> – Create a clan 

- /clan join <clan> – Join clan (if allowed) 

- /clan leave – Leave current clan 

- /clan info <clan> – View clan info 

- /clan list – Show available clans 

- /clan request <clan> allow – Accept clan request 

- /clan request <clan> deny – Deny clan request 

- /clan ignore <clan> – Ignore a clan (hide suggestions & requests) 

- /clan block <clan/player> – Block clan or player invites 

- /clan unblock <clan/player> – Unblock 

###### **Owner Commands** 

- /clan kick <player> – Remove member 

- /clan disband – Delete clan 

- /clan invite <player> – Invite player 

- /clan settings – Open settings GUI 

###### **Admin Commands** 

- /clansys delete <clan> – Remove clan 

- /clansys create <player> <limit> – New clan creation limit for players who already own a clan and have permission. 

- /clansys join <player> <limit> – Clan join limit for players who already belong to two clans 

#### **Permissions** 

###### **Basic** 

- mdn.clan.use 

- mdn.clan.create 

###### **Admin** 

- mdn.clansys.create 

- mdn.clansys.join 

- mdn.clan.admin 

###### **Limits** 

- mdn.clansys.limit.clans.<number> – override maximum number of clans an owner can create (default: 1) 

- mdn.clansys.limit.joins.<number> – override maximum number of clan joins (default: 2) 

#### **Database (MySQL)** 

Fully synchronized across all servers. 

###### **Stored data:** 

- Clan info (name, owner) 

- Members (max 8 per clan) 

- Roles (owner/member) 

- Join requests 

- Invitations 

- Blocked / ignored clans list 

###### **System behavior:** 

- Async processing (no lag) 

- Real-time multi-server sync 

- Conflict-safe updates (no duplicate joins) 

- Validation before every join/leave action 

#### **Request & Block System** 

Players can manage clan interactions: 

- Ignore clans → removes them from GUI suggestions 

- Block clans → prevents invites and requests 

- Unblock anytime 

- All actions are saved per player in MySQL 

# MDN-Moderation 

## **MDN Moderation System** 

#### **Overview** 

The **MDN Moderation System** is the central staff tool plugin for the entire Minecraft network. It provides all essential moderation, investigation, and control tools for managing players, clans, and chat systems. 

It is fully **MySQL-based and synchronized across all servers** , meaning all punishments, reports, notes, clan states, and chat controls are shared in real time across the network. 

## **Punishment System** 

#### **Features** 

- Mute system (chat restriction) 

- Ban system (temporary & permanent) 

- Kick system 

- Warn system 

- Full punishment history logging 

- Time-based punishments (e.g. 1 day , 2 hours ) 

- Fully synced across all servers (MySQL) 

#### **Commands** 

- /mute <player> <time> <reason> 

- /unmute <player> 

- /ban <player> <time> <reason> (Example: /ban Steve 1 day cheating ) 

- /unban <player> 

- /kick <player> <reason> 

- /warn <player> <reason> 

- /warnings <player> 

## **Vanish & Staff Mode** 

#### **Features** 

- Complete invisibility from players 

- No tab list visibility (optional) 

- No join/leave messages 

- No chat detection 

- Fully cross-server synchronized 

#### **Staff Mode Behavior** 

When **Vanish is enabled** , the system automatically applies: 

- Staff Mode activated 

- Fly enabled ( allowFlight = true ) 

- No collision with players (optional) 

- Completely invisible to all players 

- Cannot be detected in any way 

###### **Rule:** 

###### **Vanish = full stealth staff state (invisible + fly + undetectable)** 

#### **Commands** 

- /vanish – toggle vanish (only staff) 

- /staff – toggle staff mode (only staff, vanish + fly + tools) 

## **Clan Integration (MDN-Clan System)** 

The moderation system is fully integrated with the **MDN-Clan Plugin** . 

#### **Clan Freeze System** 

Staff can freeze: 

- Individual players 

- Entire clans (all members at once) 

###### **Effects:** 

- No movement 

- No commands 

- No interactions 

- Instant application across all servers 

###### **Commands:** 

- /freeze <player> 

- /unfreeze <player> 

- /clan freeze <clan> 

- /clan unfreeze <clan> 

## **Chat Control System** 

#### **Slow Mode System** 

Staff can control chat globally, per clan, or per player. 

###### **Features:** 

- Chat delay (slow mode) 

- Anti-spam system 

- Cross-server synchronized cooldowns 

###### **Commands:** 

###### **Global Chat** 

- /chat slowmode <seconds> 

- /chat slowmode off 

###### **Clan Chat** 

- /clan chat slowmode <clan> <seconds> 

   - Example: /clan chat slowmode Knights 5 

- /clan chat unlock <clan> 

###### **Player Chat** 

- /mutechat <player> <time> 

- /unmutechat <player> 

## **Reports System** 

- GUI-based reporting system 

- Cross-server report queue 

- Status tracking (open / reviewing / closed) 

- Staff notifications 

###### **Commands:** 

- /report <player> <reason> 

- /reports 

## **Notes System** 

- Private staff notes per player 

- Stored in MySQL 

- Used for tracking behavior and history 

###### **Commands:** 

- /note add <player> <text> 

- /note view <player> 

- /note remove <id> 

## **History System** 

Tracks all player and staff actions: 

- Punishments 

- Reports 

- Notes 

- Clan actions 

- Chat actions 

###### **Command:** 

- /history <player> 

## **Screenshare Support** 

- Freeze integration 

- Player inspection tools 

- Cheat investigation support 

- Staff checklist tools 

## **Permissions** 

#### **Basic Staff** 

- mdn.modsys.use 

- mdn.modsys.mute 

- mdn.modsys.ban 

- mdn.modsys.kick 

- mdn.mod.warn 

#### **Advanced Staff** 

- mdn.modsys.freeze 

- mdn.modsys.vanish 

- mdn.modsys.staffmode 

- mdn.modsys.reports 

- mdn.modsys.notes 

- mdn.modsys.history 

- mdn.modsys.screenshare 

#### **Clan Control** 

- mdn.modsys.clan.freeze 

- mdn.modsys.clan.chat 

- mdn.modsys.chat.slowmode 

#### **Admin** 

- mdn.modsys.admin (full access) 

## **Database (MySQL)** 

All moderation data is synchronized network-wide: 

###### **Stored Data:** 

- Punishments (ban, mute, kick, warn) 

- Player history 

- Reports 

- Notes 

- Frozen players & clans 

- Chat slow mode states 

- Staff actions 

###### **System Behavior:** 

- Async processing (no lag) 

- Real-time multi-server sync 

- Fully logged and traceable actions 

- Conflict-safe updates 

MDN-Sync 

## **MDN Sync System** 

#### **Overview** 

The **MDN-Sync System** is a cross-server synchronization plugin for the Minecraft network. It ensures that player data is consistently shared between multiple Paper servers in real time. 

The system is built to connect gameplay states across servers and provides a foundation for advanced multi-server gameplay. 

## **Core Concept** 

MDN-Sync keeps player data consistent between servers by syncing: 

- Player inventories 

- Player data (health, position where needed) 

- Item metadata 

- Custom items (ItemsAdder) 

- Custom models (ModelEngine) 

- Entity/Mob data (MythicMobs compatibility) 

All sync operations are **MySQL-based and/or cached for real-time performance** . 

## **Inventory Sync System** 

#### **Features** 

- Full inventory synchronization between 2+ Paper servers 

- Armor + offhand syncing 

- Hotbar preservation 

- Ender chest sync 

- shulker chest sync 

- Cross-server item transfer consistency 

- Anti-duplication protection 

- Async save/load system (no lag) 

#### **Behavior** 

- Inventory is saved when: 

   - Player switches server 

   - Player disconnects 

   - Manual sync trigger 

- Inventory is loaded instantly when joining another server 

- Conflict protection prevents overwriting newer data 

## **ItemsAdder Integration** 

#### **Features** 

- Full support for custom items 

- Custom NBT preservation 

- Resourcepack-safe syncing 

- Item identity tracking across servers 

#### **Behavior** 

- ItemsAdder items keep: 

   - Custom textures 

   - Custom names 

   - Custom NBT tags 

- No item loss during server switching 

## **ModelEngine Support** 

## **MythicMobs Compatibility** 

## **Sync Engine** 

#### **System Design** 

- MySQL-backed persistence layer 

- Optional caching layer (for performance) 

- Async processing (no server lag) 

- Event-driven sync system 

#### **Sync Events** 

- PlayerJoinSyncEvent (only for their Clans & Friends) 

- PlayerQuitSyncEvent (only for their Clans & Friends) 

- InventorySyncEvent 

- ItemUpdateSyncEvent 

## **Anti-Duplication System** 

- Prevents item duplication during transfers 

- Lock system during sync operations 

- Timestamp-based overwrite protection 

- Conflict resolution (latest data wins) 

## **Performance** 

- Fully asynchronous database operations 

- Minimal TPS impact 

- Chunked data transfer system 

- Optimized serialization for inventories & items 

## **Permissions** 

- mdn.syncsys.use – basic sync functionality 

- mdn.syncsys.admin – debug & manual sync tools 

- mdn.syncsys.debug – sync logging tools 

## **Developer API (Optional)** 

MDNSync.syncPlayer(player); MDNSync.saveInventory(player); MDNSync.loadInventory(player); MDNSync.forceSyncAll(); 

## **Chat Synchronization System** 

#### **Features** 

- Global chat synchronization across all connected Paper servers 

- Real-time message synchronization 

- Players can communicate seamlessly across every connected server 

- Preserves chat format, prefixes, suffixes, and placeholders 

- PlaceholderAPI compatibility 

- ItemsAdder font and glyph compatibility 

- Fully asynchronous processing 

- MySQL-based synchronization 

- No duplicate or delayed messages 

- Supports unlimited connected Paper servers 

#### **Commands** 

No additional player commands required. The system automatically synchronizes all public chat messages between connected servers. 

#### **Behavior** 

- Messages sent on one server instantly appear on every connected server. 

- Chat formatting remains identical across all servers. 

- All synchronization is handled asynchronously to prevent server lag. 

- The system ensures message order and prevents duplicate messages. 

# MDN-Discord-Sync 

## **MDN Discord Sync** 

#### **Overview** 

The **MDN Discord Sync** plugin connects the Minecraft network directly with the Discord server. It synchronizes chat messages, moderation events, and important system notifications between Minecraft and Discord in real time. 

All features are fully configurable and support multiple Discord channels. 

## **Minecraft ↔ Discord Chat Synchronization** 

#### **Features** 

- Global Minecraft chat synchronized with a Discord channel 

- Discord messages appear instantly in the Minecraft chat 

- Minecraft messages appear instantly in Discord 

- Real-time synchronization 

- Multi-server support 

- PlaceholderAPI compatibility 

- Rank prefix & suffix support 

- Customizable chat format 

- Loop protection (prevents duplicate messages) 

#### **Behavior** 

- Messages sent in Minecraft are instantly forwarded to Discord. 

- Messages sent in Discord are instantly displayed in Minecraft. 

- The sender is clearly identified on both platforms. 

- All synchronization is processed asynchronously. 

## **Moderation Integration (MDN Moderation)** 

The plugin is fully integrated with the **MDN Moderation System** . 

#### **Synced Events** 

- Bans 

- Mutes 

- Kicks 

- Warnings 

- Reports 

- Slow mode enabled/disabled 

All moderation actions can automatically be sent to configurable Discord channels. 

## **Emergency Alert System** 

#### **Features** 

The plugin automatically sends critical events to Discord. 

###### **Examples** 

- Server emergencies 

- Anti-cheat alerts 

- Multiple reports against the same player 

- Suspicious player activity 

- Plugin errors 

- Database failures 

- Network failures 

- Server crashes 

## **Discord Team Alerts** 

Critical events automatically trigger an alert in the staff Discord channels. 

###### **Features** 

- Ping configurable Discord roles (e.g. **@Staff** , **@Moderator** , **@Admin** ) 

- Dedicated emergency channel 

- Priority levels (Info, Warning, Critical) 

- Timestamp on every alert 

- Server name included in every notification 

- Fully customizable Discord embeds 

## **System Notifications** 

The plugin can automatically send notifications for: 

- Server startup 

- Server shutdown 

- Server restart 

- Maintenance mode enabled/disabled 

- Network announcements 

- Plugin errors 

- Synchronization failures 

# MDN-Ingame-Shop 

## **MDN Ingame Shop** 

#### **Overview** 

The **MDN Ingame Shop** is the official server shop where players can purchase items using the network's economy system. 

The plugin is fully integrated with the **MDN Economy System** , supports **Vault** , and is compatible with **ItemsAdder** , allowing both vanilla and custom items to be sold. 

## **Economy Integration** 

#### **Features** 

- Full Vault support 

- Integration with the MDN Economy System 

- Secure transaction handling 

- Balance verification before every purchase 

- Purchase logging 

- Async database processing 

## **Shop System** 

#### **Features** 

- GUI-based shop 

- Category navigation 

- Search function 

- Buy items with coins 

- Configurable item prices 

- Unlimited shop items 

- Buy multiple items at once 

- Customizable shop layout 

● Instant item delivery 

## **ItemsAdder Integration** 

#### **Features** 

- Full support for ItemsAdder custom items 

- Custom textures and models 

- Support for custom fonts and icons 

- Custom item metadata preserved 

- Buy any registered ItemsAdder item 

## **Shop Categories** 

The shop should support configurable categories, for example: 

- Building Blocks (16x) 

- Decoration 

- Ores & Minerals 

- Farming 

- Food 

- Tools 

- Weapons 

- Armor 

- Redstone 

- Miscellaneous 

- Custom Items (ItemsAdder) 

All categories should be configurable. 

## **GUI Features** 

Players can: 

- Browse categories 

- View item prices 

- Search for items 

- Purchase items 

- View their current balance 

- Instantly receive purchased items 

###### **GUI Actions** 

- **Left Click** → Buy 1 item 

- **Right Click** → Buy 1 item 

## **Commands** 

###### **Player Commands** 

- /shop – Open the main shop 

- /shop search <item> – Search for an item 

- /shop reload _(Admin)_ – Reload the shop configuration 

## **Permissions** 

###### **Player** 

- mdn.shop.use 

- mdn.shop.buy 

###### **Admin** 

- mdn.shopsys.reload 

- mdn.shopsys.admin 

## **Database** 

###### **Stored Data** 

- Purchase history (optional) 

- Shop statistics 

- Player purchase logs 

###### **System Behavior** 

- Async processing 

- Economy transaction logging 

- Conflict-safe purchases 

- No duplication exploits 

## **Compatibility** 

Fully compatible with: 

- MDN Economy 

- Vault 

- ItemsAdder 

- PlaceholderAPI 

- Paper 1.21+ 

# MDN-SAM 

## **MDN SAM (Steal A Mineling) Description** 

#### **Overview** 

The **MDN SAM (Steal A Mineling)** plugin is the core gameplay plugin of the MineDrop network. It manages the complete game logic, including arenas, conveyor belts, Minelings, player bases, NPCs, events, and gameplay mechanics. 

The plugin is designed for large Minecraft networks and is fully compatible with **ItemsAdder** , **MythicMobs** , **ModelEngine** , **Vault** , and **PlaceholderAPI** . 

## **Arena Management** 

#### **Features** 

- Arena loading 

- Arena editing 

- Arena validation 

- Arena templates 

- Arena rotation 

- Arena enable/disable 

- Automatic arena startup 

- Automatic arena reset 

- Spawn location management 

- Region management 

- World protection 

## **Spawn System** 

Configurable spawn locations for: 

- Lobby Spawn 

- Arena Spawn 

- Spectator Spawn 

- Conveyor Spawn 

- Merchant NPC Spawn 

- Event Spawn 

- Boss Spawn 

## **Conveyor Belt System** 

The Conveyor Belt is the central gameplay mechanic. 

#### **Features** 

- Fully configurable conveyor belts 

- Adjustable speed 

- Adjustable spawn interval 

- Multiple conveyor belts per arena 

- Automatic Mineling spawning 

- Automatic despawn at belt end 

- Smooth movement animation 

- Supports unlimited Mineling types 

- Supports custom conveyor layouts 

## **Mineling System** 

Players capture Minelings directly from the conveyor belt. 

#### **Features** 

- Click-to-capture system 

- Automatic economy payment 

- Capture validation 

- Mineling storage 

- Sell Minelings to NPCs 

- Place Minelings inside bases 

- Configurable rarity system 

#### **Supported Rarities** 

- Common 

- Rare 

- Epic 

- Legendary 

- Mythic 

- Brainrot 

Each rarity supports: 

- Custom color 

- Spawn chance 

- Coin multiplier 

- Passive income 

- Custom model 

- Custom animation 

- Custom sounds 

## **Passive Income System** 

Every placed Mineling generates coins automatically. 

#### **Features** 

- Passive coin generation 

- Configurable income interval 

- Rarity multipliers 

- Global income multipliers 

- Event multipliers 

- Automatic Vault integration 

## **Mineling Storage** 

Captured Minelings are stored separately. 

#### **Features** 

- Dedicated storage GUI 

- Search system 

- Sorting 

- Quick placement 

- Quick selling 

- Unlimited pages 

- Favorite Minelings 

## **Destroyer System** 

Random Destroyers attack player bases. 

#### **Features** 

- Random spawning 

- Warning messages 

- Siren sound 

- Random target selection 

- Intelligent pathfinding 

- Block destruction 

- Configurable attack strength 

- Configurable spawn chances 

- Multiple Destroyer rarities 

- Boss health bar 

## **Case Opening System** 

Players can obtain rewards from cases. 

#### **Features** 

- NPC interaction 

- Animated opening 

- Custom rewards 

- Reward previews 

- Configurable loot tables 

- Mystery Boxes 

- Cosmetics 

- Coins 

- Special Minelings 

## **NPC Merchant System** 

Interactive NPCs allow players to: 

- Sell Minelings 

- Buy decorations 

- Buy building blocks 

- Buy tools 

- Buy protection upgrades 

- Buy cosmetic items 

- Buy special items 

## **Base System** 

Each player owns a protected base. 

#### **Features** 

- Base generation 

- Base upgrades 

- Decoration support 

- Building permissions 

- Protection zones 

- Upgrade system 

- Security upgrades 

## **Stealth System** 

Players can steal Minelings from other bases. 

#### **Features** 

- Enter enemy bases 

- Capture enemy Minelings 

- Escape with stolen Minelings 

- Anti-abuse protection 

- Cooldowns 

- Configurable stealing rules 

## **Leaderboards** 

Player statistics: 

- Coins 

- Passive Income 

- Minelings Captured 

- Minelings Owned 

- Minelings Stolen 

- Prestige Minelings 

- Destroyers Defeated 

## **Event System** 

Automatic server events. 

Examples: 

- Double Coins 

- Lucky Conveyor 

- Double Spawn Rate 

- Destroyer Event 

- Rare Spawn Event 

- Brainrot Event 

## **Compatibility** 

Fully compatible with: 

- ItemsAdder 

- MythicMobs 

- ModelEngine 

- PlotSquared 

- Vault 

- PlaceholderAPI 

- MDN Economy 

- MDN Auction House 

- MDN Clan 

- Paper 1.21+ 

## **Commands** 

###### **Player** 

- /sam 

- /mineling 

- /base 

- /cases 

###### **Admin** 

- /sam freeze <clan> 

- /sam unfreeze <clan> 

## **Permissions** 

###### **Admin** 

- mdn.samsys.admin 

- mdn.sam.freeze 

- mdn.sam.unfreeze 

- mdn.sam.reload 

## **Database (MySQL)** 

#### **Stored Data** 

- Arena data 

- Player bases 

- Conveyor layouts 

- Minelings 

- Passive income 

- Spawn locations 

- Events 

- Statistics 

- Destroyer data 

- Cases 

- Player progression 

#### **System Behavior** 

- Fully asynchronous 

- Real-time synchronization 

- Multi-server support 

- Automatic backups 

- Conflict-safe processing 

- Optimized for large player counts 

# MDN-Encryption 

## **MDN Bridge Core – Secure Dependency System** 

#### **Overview** 

The **MDN Bridge Core** is the mandatory core system for all MDN plugins. Without it, no MDN plugin (SAM, Clan, Economy, Moderation, Sync, etc.) will function. 

It acts as a **security gateway + API validation layer** , ensuring that only official MDN plugins can operate inside the network. 

## **Core Concept** 

Every MDN plugin contains a **hidden internal API key** that is: 

- embedded deep inside the plugin code 

- encrypted / obfuscated 

- not editable via config 

- bound to the MDN Bridge Core 

If the key is missing or invalid → plugin disables itself 

## **Dependency System** 

#### **Startup Flow** 

When a plugin starts: 

1. Plugin tries to connect to MDN Bridge Core 

2. Plugin sends internal handshake request 

3. Bridge Core validates: 

   - Plugin ID 

   - Internal API key 

   - Server identity 

4. If valid → plugin is activated 

5. If invalid → plugin shuts down or goes into **disabled mode** 

## **Security Mechanism** 

#### **Features** 

- Hardcoded encrypted API key inside each plugin 

- Server-bound authentication 

- Plugin signature verification 

- Bridge Core whitelist validation 

- Anti-tamper detection 

- Runtime integrity checks 

## **Without Bridge Core** 

If MDN Bridge Core is NOT installed: 

- SAM does NOT start 

- Clan system is disabled 

- Economy stops working 

- Moderation tools are locked 

- Sync system is inactive 

The whole network becomes non-functional 

## **API Key System** 

Each plugin contains: 

###### **Internal Data** 

- Plugin ID (e.g. MDN-SAM ) 

- Secret API Key (encrypted) 

- Version signature 

- Build hash 

###### **Example (conceptual)** 

MDN-SAM: 

id: sam_core 

key: ***ENCRYPTED_INTERNAL_KEY*** 

## **Bridge Core Validation** 

The Bridge Core checks: 

- Is the MDN plugin official? 

- Does API key match? 

- Is plugin modified? 

- Is server authorized? 

- Is version compatible? 

Only then: 

- ✔ Plugin is enabled 

- ✔ Events are allowed 

- ✔ API communication starts 

## **Runtime Protection** 

During runtime: 

- Continuous heartbeat checks 

- Key revalidation 

- Anti-injection detection 

- Packet integrity checks 

- Automatic shutdown on tampering 

## **System Behavior** 

**State Result** 

Valid key + Bridge Plugin fully Core active Missing Bridge Core Plugin disabled Invalid key Plugin blocked Modified plugin Instant shutdown 

## **Goal** 

The goal of the **MDN Bridge Core Security System** is to ensure: 

- Only official MDN plugins can run 

- No fake or modified plugins work 

- All systems are securely connected 

- The entire network behaves like one controlled ecosystem 

