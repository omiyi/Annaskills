package dev.aurelium.auraskills.bukkit.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.aurelium.auraskills.bukkit.AuraSkills;
import dev.aurelium.auraskills.common.message.type.CommandMessage;
import dev.aurelium.auraskills.common.util.text.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.VisibleForTesting;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Optional;
import java.util.function.BiConsumer;

public class UpdateChecker {

    public static final String GITHUB_REPOSITORY = "omiyi/Annaskills";

    private final AuraSkills plugin;
    public UpdateChecker(AuraSkills plugin) {
        this.plugin = plugin;
    }

    public void sendUpdateMessageAsync(CommandSender sender) {
        getVersion((versionOpt, idOpt) -> versionOpt.ifPresent(version -> idOpt.ifPresent(id -> {
            if (isOutdated(plugin.getDescription().getVersion(), version)) {
                final String prefix = sender instanceof Player ? plugin.getPrefix(plugin.getDefaultLanguage()) : "[Annaskills] ";

                String msg = TextUtil.replace(plugin.getMsg(CommandMessage.VERSION_NEW_UPDATE, plugin.getLocale(sender)),
                        "{current_version}", plugin.getDescription().getVersion(),
                        "{latest_version}", version,
                        "{link}", id,
                        "{prefix}", prefix);

                if (!msg.isEmpty()) {
                    plugin.getScheduler().executeAtCommandSender(sender, () -> sender.sendMessage(msg));
                }
            }
        })));
    }

    // Consumer accepts versionNumber and versionId
    public void getVersion(final BiConsumer<Optional<String>, Optional<String>> consumer) {
        plugin.getScheduler().executeAsync(() -> {
            final String url = "https://api.github.com/repos/" + GITHUB_REPOSITORY + "/releases/latest";
            try (HttpClient client = HttpClient.newHttpClient()) {

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("Accept", "application/vnd.github+json")
                        .header("User-Agent", "Annaskills-UpdateChecker")
                        .GET()
                        .build();

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    String responseBody = response.body();
                    if (responseBody == null || responseBody.trim().isEmpty()) {
                        acceptEmpty(consumer);
                        return;
                    }

                    JsonObject release = JsonParser.parseString(responseBody).getAsJsonObject();
                    JsonElement versionNumElement = release.get("tag_name");
                    if (versionNumElement == null) {
                        acceptEmpty(consumer);
                        return;
                    }
                    String versionNumber = versionNumElement.getAsString().replaceFirst("^[vV]", "");

                    JsonElement urlElement = release.get("html_url");
                    if (urlElement == null) {
                        acceptEmpty(consumer);
                        return;
                    }
                    String releaseUrl = urlElement.getAsString();

                    consumer.accept(Optional.of(versionNumber), Optional.of(releaseUrl));
                    return;
                } else {
                    this.plugin.getLogger().info("Cannot look for updates: Request failed with status code " + response.statusCode());
                }
            } catch (Exception e) {
                this.plugin.getLogger().info("Cannot look for updates: " + e.getMessage());
            }
            acceptEmpty(consumer);
        });
    }

    private void acceptEmpty(BiConsumer<Optional<String>, Optional<String>> consumer) {
        consumer.accept(Optional.empty(), Optional.empty());
    }

    @VisibleForTesting
    public boolean isOutdated(String localVersion, String resourceVersion) {
        if (localVersion.equalsIgnoreCase(resourceVersion)) { // Versions match exactly
            return false;
        }
        String[] localSplit = localVersion.split(" ");
        String[] resourceSplit = resourceVersion.split(" ");
        String localNum;
        String resourceNum;
        if (localSplit.length >= 2 && resourceSplit.length >= 2) {
            localNum = localSplit[1];
        } else {
            localNum = localSplit[0];
        }
        if (resourceSplit.length >= 2) {
            resourceNum = resourceSplit[1];
        } else {
            resourceNum = resourceSplit[0];
        }
        // Remove part after any hyphens including the hyphen
        int localIndex = localNum.indexOf("-");
        int localSuffix = getSuffix(localNum);
        if (localIndex != -1) {
            localNum = localNum.substring(0, localIndex);
        }
        int resourceIndex = resourceNum.indexOf("-");
        int resourceSuffix = getSuffix(resourceNum);
        if (resourceIndex != -1) {
            resourceNum = resourceNum.substring(0, resourceIndex);
        }

        String[] localVersionSplit = localNum.split("\\.");
        String[] resourceVersionSplit = resourceNum.split("\\.");

        // Check each part of the version number between the dots
        for (int i = 0; i < localVersionSplit.length; i++) {
            if (i >= resourceVersionSplit.length) {
                break;
            }
            try {
                int local = Integer.parseInt(localVersionSplit[i]);
                int resource = Integer.parseInt(resourceVersionSplit[i]);
                if (local < resource) { // If local is less than resource, return as outdated
                    return true;
                } else if (local > resource) { // If local is greater than resource, return as not outdated
                    return false;
                }
            } catch (NumberFormatException ignored) {
            }
        }
        if (localSuffix != -1 && resourceSuffix != -1) {
            return resourceSuffix > localSuffix;
        }
        return true;
    }

    private int getSuffix(String str) {
        int resourceIndex = str.indexOf("-");
        if (resourceIndex != -1) {
            String suffixStr = str.substring(resourceIndex + 1);
            if (suffixStr.contains("+")) {
                String[] arr = dev.aurelium.slate.util.TextUtil.substringsBetween(suffixStr, ".", "+");
                if (arr != null && arr.length >= 1) {
                    try {
                        return Integer.parseInt(arr[0]);
                    } catch (NumberFormatException ignored) {
                    }
                }
            } else {
                String buildNumStr = suffixStr.substring(suffixStr.indexOf(".") + 1);
                try {
                    return Integer.parseInt(buildNumStr);
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return -1;
    }

}
