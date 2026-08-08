package us.talabrek.ultimateskyblock.island;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Test;
import us.talabrek.ultimateskyblock.uSkyBlock;

import java.util.Arrays;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;

public class IslandInfoConfigCopyTest {

    @Test
    public void copiesCompleteConfigurationWithoutSharingMutableSections() {
        IslandInfo source = new IslandInfo("copy-test-" + UUID.randomUUID(), mock(uSkyBlock.class));
        UUID banned = UUID.randomUUID();
        YamlConfiguration config = new YamlConfiguration();
        config.set("general.level", 123.5);
        config.set("general.schematicName", "expert");
        config.set("general.flags.pvp", true);
        config.set("party.members.member.maxAnimals", 17);
        config.set("trust.list", Arrays.asList(UUID.randomUUID().toString()));
        config.set("banned.list", Arrays.asList(banned.toString()));
        config.set("log", Arrays.asList("one", "two"));
        source.setConfig(config);

        FileConfiguration copy = source.copyConfig();

        assertThat(copy.saveToString(), is(config.saveToString()));
        assertThat(source.getBannedUUIDs(), contains(banned));
        copy.set("general.level", 0);
        copy.set("party.members.member.maxAnimals", 1);
        assertThat(config.getDouble("general.level"), is(123.5));
        assertThat(config.getInt("party.members.member.maxAnimals"), is(17));
    }
}
