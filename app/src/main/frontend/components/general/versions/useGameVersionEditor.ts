import {useCallback, useEffect, useMemo, useRef, useState} from "react";
import {toast} from "@heroui/react";
import {GameStoreLinkEndpoint, GameVersionEndpoint} from "Frontend/generated/endpoints";
import GameStore from "Frontend/generated/org/gameyfin/app/games/versions/GameStore";

type VersionStatus = Awaited<ReturnType<typeof GameVersionEndpoint.getStatus>>[number];

export type StoreVersionOption = VersionStatus["versions"][number];

/** A store link as shown in the editor. Edits are staged here and only written to the server by [apply]. */
export interface StagedLink {
    key: string;
    /** Undefined for links that were added in the editor but not saved yet. */
    linkId?: number;
    store: GameStore;
    storeAppId: string;
    title?: string;
    tracked: boolean;
    originalTracked: boolean;
    versions: StoreVersionOption[];
    versionsCheckedAt?: string;
    installedVersionId?: number;
    originalInstalledVersionId?: number;
    /** The staged library version was detected instead of selected by hand. */
    detected: boolean;
    removed: boolean;
    /** Versions of a link that is not saved yet use negative IDs, which index into this list. */
    previewBuilds?: { buildId: string; branch: string }[];
    loadingVersions?: boolean;
}

function errorMessage(error: unknown) {
    return error instanceof Error ? error.message : "Please try again.";
}

function fromStatus(status: VersionStatus): StagedLink {
    return {
        key: `link-${status.linkId}`,
        linkId: status.linkId,
        store: status.store,
        storeAppId: status.storeAppId,
        tracked: status.tracked,
        originalTracked: status.tracked,
        versions: status.versions,
        versionsCheckedAt: status.versionsCheckedAt,
        installedVersionId: status.installedVersionId,
        originalInstalledVersionId: status.installedVersionId,
        detected: status.installedVersionSource === "AUTO",
        removed: false,
    };
}

export function useGameVersionEditor(gameId: number, isOpen: boolean) {
    const [links, setLinks] = useState<StagedLink[]>([]);
    const [loading, setLoading] = useState(false);
    const [pendingKey, setPendingKey] = useState<string>();
    const newCounter = useRef(0);

    // Reloading when the dialog opens discards edits that were cancelled
    useEffect(() => {
        if (!isOpen) return;
        let cancelled = false;
        setLoading(true);
        GameVersionEndpoint.getStatus(gameId)
            .then((statuses) => {
                if (!cancelled) setLinks(statuses.map(fromStatus));
            })
            .catch((error) => toast.danger("Could not load game versions", {description: errorMessage(error)}))
            .finally(() => {
                if (!cancelled) setLoading(false);
            });
        return () => {
            cancelled = true;
        };
    }, [gameId, isOpen]);

    const visibleLinks = useMemo(() => links.filter((link) => !link.removed), [links]);

    const dirty = useMemo(() => links.some((link) =>
        link.linkId === undefined
            ? !link.removed
            : link.removed
            || link.installedVersionId !== link.originalInstalledVersionId
            || link.tracked !== link.originalTracked
    ), [links]);

    const update = useCallback((key: string, change: Partial<StagedLink>) => {
        setLinks((current) => current.map((link) => link.key === key ? {...link, ...change} : link));
    }, []);

    /** Fetches the versions of a store product that is not linked yet, without saving anything. */
    const loadPreview = useCallback(async (key: string, store: GameStore, storeAppId: string, overrideSelection = false) => {
        update(key, {loadingVersions: true});
        try {
            const preview = await GameVersionEndpoint.previewVersions(gameId, store, storeAppId);
            const versions = preview.versions.map((version, index) => ({
                id: -(index + 1),
                buildId: version.buildId,
                branch: version.branch,
                label: version.label,
                releasedAt: version.releasedAt,
            }));
            setLinks((current) => current.map((link) => {
                if (link.key !== key) return link;
                const detectedId = preview.detectedIndex == null ? undefined : -(preview.detectedIndex + 1);
                const keepSelection = link.installedVersionId !== undefined && (overrideSelection ? detectedId === undefined : !link.detected);
                return {
                    ...link,
                    versions,
                    previewBuilds: preview.versions.map(({buildId, branch}) => ({buildId, branch})),
                    installedVersionId: keepSelection ? link.installedVersionId : detectedId,
                    detected: keepSelection ? link.detected : detectedId !== undefined,
                    loadingVersions: false,
                };
            }));
            if (preview.detectedIndex != null) {
                toast.success("Library version detected", {description: preview.versions[preview.detectedIndex].label});
            } else if (overrideSelection) {
                toast.info("Version not detected", {description: "No library version matching the cached builds was detected"});
            }
        } catch (error) {
            update(key, {loadingVersions: false});
            toast.warning("Could not fetch versions", {
                description: errorMessage(error) + " You can still add the store and use Refresh after saving.",
            });
        }
    }, [gameId, update]);

    const addLink = useCallback((store: GameStore, storeAppId: string, title?: string) => {
        const duplicate = links.some((link) => !link.removed && link.store === store && link.storeAppId === storeAppId);
        if (duplicate) return;

        const removedMatch = links.find((link) => link.removed && link.store === store && link.storeAppId === storeAppId);
        if (removedMatch) {
            update(removedMatch.key, {removed: false});
            return;
        }

        const key = `new-${newCounter.current++}`;
        setLinks((current) => [...current, {
            key, store, storeAppId, title,
            tracked: false, originalTracked: false,
            versions: [], detected: false, removed: false,
        }]);
        void loadPreview(key, store, storeAppId);
    }, [links, loadPreview, update]);

    const refreshPreview = useCallback((key: string) => {
        const link = links.find((candidate) => candidate.key === key);
        if (link && link.linkId === undefined) void loadPreview(key, link.store, link.storeAppId);
    }, [links, loadPreview]);

    const removeLink = useCallback((key: string) => {
        setLinks((current) => current
            .filter((link) => !(link.key === key && link.linkId === undefined))
            .map((link) => link.key === key ? {...link, removed: true} : link));
    }, []);

    const setInstalledVersion = useCallback((key: string, versionId?: number) => {
        update(key, {installedVersionId: versionId, detected: false});
    }, [update]);

    const makePrimary = useCallback((key: string) => {
        setLinks((current) => current.map((link) => ({...link, tracked: link.key === key})));
    }, []);

    const detect = useCallback(async (key: string) => {
        const link = links.find((candidate) => candidate.key === key);
        if (!link) return;
        if (link.linkId === undefined) {
            await loadPreview(link.key, link.store, link.storeAppId, true);
            return;
        }
        setPendingKey(key);
        try {
            const result = await GameVersionEndpoint.previewDetection(link.linkId);
            if (result.detected) {
                update(key, {installedVersionId: result.installedVersionId, detected: true});
                toast.success("Library version detected", {description: result.message});
            } else {
                toast.info("Version not detected", {description: result.message});
            }
        } catch (error) {
            toast.danger("Could not detect library version", {description: errorMessage(error)});
        } finally {
            setPendingKey(undefined);
        }
    }, [links, update, loadPreview]);

    /** Refreshing only updates the cached store versions, so it takes effect immediately. */
    const refresh = useCallback(async (key: string) => {
        const link = links.find((candidate) => candidate.key === key);
        if (!link) return;
        if (link.linkId === undefined) {
            await loadPreview(link.key, link.store, link.storeAppId, true);
            return;
        }
        setPendingKey(key);
        try {
            await GameVersionEndpoint.refreshVersions(link.linkId);
            const status = (await GameVersionEndpoint.getStatus(gameId)).find((s) => s.linkId === link.linkId);
            if (status) update(key, {versions: status.versions, versionsCheckedAt: status.versionsCheckedAt});
            toast.success("Versions refreshed");
        } catch (error) {
            toast.danger("Could not refresh versions", {description: errorMessage(error)});
        } finally {
            setPendingKey(undefined);
        }
    }, [gameId, links, update]);

    /** Writes all staged edits to the server. Throws if a step fails, after reloading the actual state. */
    const apply = useCallback(async () => {
        if (!dirty) return;
        try {
            for (const link of links.filter((l) => l.removed && l.linkId !== undefined)) {
                await GameStoreLinkEndpoint.removeLink(link.linkId!);
            }

            for (const link of links.filter((l) => l.linkId === undefined && !l.removed)) {
                const created = await GameStoreLinkEndpoint.addLink(gameId, link.store, link.storeAppId);
                try {
                    await GameVersionEndpoint.refreshVersions(created.id);
                    const staged = link.previewBuilds?.[-(link.installedVersionId ?? 0) - 1];
                    if (staged) {
                        const status = (await GameVersionEndpoint.getStatus(gameId)).find((s) => s.linkId === created.id);
                        const match = status?.versions.find((v) => v.buildId === staged.buildId && v.branch === staged.branch);
                        if (match) await GameVersionEndpoint.setInstalledVersion(created.id, match.id, link.detected);
                    } else if (link.installedVersionId === undefined && !link.previewBuilds) {
                        // The versions could not be previewed, so detection is the best remaining option
                        await GameVersionEndpoint.detectInstalledVersion(created.id);
                    }
                } catch (error) {
                    toast.warning(`${link.store} was added, but its versions could not be fetched`, {
                        description: errorMessage(error) + " Use Refresh to try again.",
                    });
                }
            }

            const existing = links.filter((l) => l.linkId !== undefined && !l.removed);
            const newPrimary = existing.find((l) => l.tracked && !l.originalTracked);
            if (newPrimary) await GameStoreLinkEndpoint.setTracked(newPrimary.linkId!);

            for (const link of existing.filter((l) => l.installedVersionId !== l.originalInstalledVersionId)) {
                await GameVersionEndpoint.setInstalledVersion(link.linkId!, link.installedVersionId, link.detected);
            }
        } catch (error) {
            toast.danger("Could not save store versions", {description: errorMessage(error)});
            setLinks((await GameVersionEndpoint.getStatus(gameId)).map(fromStatus));
            throw error;
        }
    }, [dirty, gameId, links]);

    return {
        links: visibleLinks, loading, pendingKey, dirty,
        addLink, removeLink, setInstalledVersion, makePrimary, detect, refresh, refreshPreview, apply,
    };
}

export type GameVersionEditorState = ReturnType<typeof useGameVersionEditor>;
