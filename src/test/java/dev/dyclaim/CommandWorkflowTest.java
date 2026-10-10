package dev.dyclaim;

import dev.dyclaim.command.ClaimCommand;
import dev.dyclaim.manager.MessageManager;
import org.bukkit.command.ConsoleCommandSender;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CommandWorkflowTest extends ManagerFixture {
    private ConsoleCommandSender console() {
        var sender=mock(ConsoleCommandSender.class);when(sender.hasPermission("dyclaim.admin")).thenReturn(true);return sender;
    }
    @ParameterizedTest @ValueSource(strings={"reload","yenile"})
    void consoleCanReloadWithEitherLanguage(String word) {
        var sender=console();new ClaimCommand(plugin).onCommand(sender,null,"claim",new String[]{"admin",word});
        verify(plugin).reload();verify(sender).sendMessage("reload-success");verify(sender,never()).sendMessage("player-only");
    }
    @Test void consoleReloadRequiresPermissionAndReportsInvalidConfig() {
        var sender=console();var command=new ClaimCommand(plugin);when(sender.hasPermission("dyclaim.admin")).thenReturn(false);
        command.onCommand(sender,null,"claim",new String[]{"admin","reload"});verify(plugin,never()).reload();verify(sender).sendMessage("no-permission");
        when(sender.hasPermission("dyclaim.admin")).thenReturn(true);doThrow(new IllegalArgumentException("invalid test config")).when(plugin).reload();
        command.onCommand(sender,null,"claim",new String[]{"admin","reload"});verify(sender).sendMessage("reload-invalid");
    }
    @Test void consoleCanSetGlobalOptionsButCannotUseLocationActions() {
        var sender=console();var command=new ClaimCommand(plugin);
        command.onCommand(sender,null,"claim",new String[]{"admin","fiyat","250"});verify(config).setClaimPrice(250);
        command.onCommand(sender,null,"claim",new String[]{"admin","give","Owner"});verify(sender).sendMessage("player-only");
        assertEquals(List.of("admin","help"),command.onTabComplete(sender,null,"claim",new String[]{""}));
        var leaves=command.onTabComplete(sender,null,"claim",new String[]{"admin",""});assertTrue(leaves.contains("reload"));assertFalse(leaves.contains("give"));assertFalse(leaves.contains("purge"));
        assertTrue(command.onTabComplete(sender,null,"claim",new String[]{"admin","give",""}).isEmpty());
    }
    @Test void helpGroupsExplosionsAndConsoleHelpOmitsPlayerOnlyActions() {
        when(plugin.getMessageManager().getPrefix()).thenReturn("[DyClaim]");
        when(plugin.getMessageManager().getMessage(any(org.bukkit.command.CommandSender.class),anyString(),anyMap())).thenAnswer(i->i.getArgument(1));
        var owner=player("Owner",chunk(0,0));var command=new ClaimCommand(plugin);
        command.onCommand(owner,null,"claim",new String[]{"help"});verify(owner).sendMessage("help-explosion");verify(owner,never()).sendMessage("help-mobexplosion");
        var sender=console();command.onCommand(sender,null,"claim",new String[]{"admin","help"});
        verify(sender).sendMessage("admin-help-reload");verify(sender).sendMessage("admin-help-price");verify(sender,never()).sendMessage("admin-help-give");verify(sender,never()).sendMessage("admin-help-purge");
    }
    @Test void invalidReloadSyntaxDoesNotReloadAndTrustNormalizationKeepsNames() {
        var sender=console();new ClaimCommand(plugin).onCommand(sender,null,"claim",new String[]{"admin","reload","typo"});verify(plugin,never()).reload();
        var words=new String[]{"güven","Kalıcı","sandıklar","reddet"};
        var normalized=dev.dyclaim.util.CommandWords.normalize(words);
        assertArrayEquals(new String[]{"güven","Kalıcı","containers","deny"},normalized);
        assertArrayEquals(normalized,dev.dyclaim.util.CommandWords.normalize(normalized));
    }
    @Test void consolidatedTrustEditsRightsWithoutReplacingTheGrant() {
        var inside=chunk(0,0);var owner=player("Owner",inside);var friend=player("Friend",inside);claims.claimChunk(owner,inside);
        features.handle(owner,new String[]{"güven","Friend","2h"});var grant=claims.getClaimAt(inside).getTrustGrants().get(friend.getUniqueId());long expiry=grant.expiresAt;
        features.handle(owner,new String[]{"güven","Friend","sandıklar","reddet"});
        assertFalse(grant.permissions.contains("containers"));assertEquals(expiry,grant.expiresAt);
        features.handle(owner,new String[]{"trust","Friend","containers","allow"});assertTrue(grant.permissions.contains("containers"));
        features.handle(owner,new String[]{"trustperm","Friend","containers","deny"});assertFalse(grant.permissions.contains("containers"));
    }
    @Test void trustPermissionDenialHasOnlyThePermissionMessage() {
        var owner=player("Owner",chunk(0,0));when(owner.hasPermission("dyclaim.trust")).thenReturn(false);
        features.handle(owner,new String[]{"trustperm","Friend","containers","deny"});verify(owner).sendMessage("no-permission");verify(owner,never()).sendMessage("feature-disabled");
    }
    @Test void explosionMobSelectorTogglesOnlyItsOwnChoiceAndOldAliasWorks() {
        var inside=chunk(0,0);var owner=player("Owner",inside);claims.claimChunk(owner,inside);var claim=claims.getClaimAt(inside);
        claim.setChoice("explosions",false);claim.setChoice("mob-explosions",false);
        features.handle(owner,new String[]{"patlama","mob"});assertTrue(access.enabled(claim,"mob-explosions"));assertFalse(access.enabled(claim,"explosions"));
        features.handle(owner,new String[]{"mobpatlama"});assertFalse(access.enabled(claim,"mob-explosions"));
        features.handle(owner,new String[]{"explosion"});assertTrue(access.enabled(claim,"explosions"));
    }
    @Test void invalidExplosionArgumentsCannotToggleAnything() {
        var inside=chunk(0,0);var owner=player("Owner",inside);claims.claimChunk(owner,inside);var claim=claims.getClaimAt(inside);
        claim.setChoice("explosions",false);claim.setChoice("mob-explosions",false);
        for(String[] args:List.of(new String[]{"patlama","typo"},new String[]{"explosion","mob","typo"},new String[]{"mobexplosion","typo"}))features.handle(owner,args);
        assertFalse(access.enabled(claim,"explosions"));assertFalse(access.enabled(claim,"mob-explosions"));
    }
    @Test void tabShowsGroupedCommandsAndLocalizedRightsWithLeafChecks() {
        var owner=player("Owner",chunk(0,0));when(plugin.getMessageManager().getPreferredLanguage(owner)).thenReturn("tr");var command=new ClaimCommand(plugin);
        var roots=command.onTabComplete(owner,null,"claim",new String[]{""});assertTrue(roots.containsAll(List.of("patlama","güven")));assertFalse(roots.contains("mobpatlama"));assertFalse(roots.contains("güvenizin"));
        assertEquals(List.of("mob"),command.onTabComplete(owner,null,"claim",new String[]{"patlama",""}));
        assertEquals(List.of("sandıklar"),command.onTabComplete(owner,null,"claim",new String[]{"güven","Friend","sandi"}));
        assertEquals(List.of("izinver","reddet"),command.onTabComplete(owner,null,"claim",new String[]{"trust","Friend","containers",""}));
        when(owner.hasPermission("dyclaim.trust")).thenReturn(false);assertTrue(command.onTabComplete(owner,null,"claim",new String[]{"trust","Friend",""}).isEmpty());
    }
    @Test void existingDefaultHelpMigratesWhileCustomTextSurvives() throws Exception {
        when(config.getLang()).thenReturn("en");when(plugin.getResource(anyString())).thenAnswer(i->getClass().getResourceAsStream("/"+i.getArgument(0)));
        String old="help-explosion: \"&6/claim explosion &7- Toggle explosions (in your claim)\"\nhelp-trustperm: \"&6/claim trustperm <player> <right> <allow|deny> &7- Set a trusted player's access rights\"\nhelp-trust: 'Custom trust help'\n";
        Files.writeString(dir.resolve("messages_en.yml"),old,StandardCharsets.UTF_8);var messages=new MessageManager(plugin);
        assertTrue(messages.getRawMessage("help-explosion").contains("explosion [mob]"));assertTrue(messages.getRawMessage("help-trustperm").contains("/claim trust <player> <right>"));
        assertEquals("Custom trust help",messages.getRawMessage("help-trust"));assertEquals(old,Files.readString(dir.resolve("messages_en.yml")));
    }
    @Test void turkishUsageDefaultsMigrateWithoutChangingCustomExplosionHelp() throws Exception {
        when(config.getLang()).thenReturn("tr");when(plugin.getResource(anyString())).thenAnswer(i->getClass().getResourceAsStream("/"+i.getArgument(0)));
        var previous=new org.bukkit.configuration.file.YamlConfiguration();
        var defaults=new org.bukkit.configuration.file.YamlConfiguration();defaults.loadFromString(new String(getClass().getResourceAsStream("/messages_tr.yml").readAllBytes(),StandardCharsets.UTF_8));
        previous.set("trustperm-usage",defaults.getString("trustperm-usage").replace("/claim güven ","/claim güvenizin ").replace("&cKullanım: ",""));
        previous.set("help-explosion","Custom explosion help");previous.save(dir.resolve("messages_tr.yml").toFile());
        var messages=new MessageManager(plugin);assertEquals(defaults.getString("trustperm-usage"),messages.getRawMessage("trustperm-usage"));assertEquals("Custom explosion help",messages.getRawMessage("help-explosion"));
    }
}
