import React, {useEffect, useState} from "react";
import {Button, Chip} from "@heroui/react";
import {GameDlcEndpoint, GameVersionEndpoint} from "Frontend/generated/endpoints";
import GameIncludedDlcDto from "Frontend/generated/org/gameyfin/app/games/versions/GameIncludedDlcDto";

interface GameVersionDetailsProps {
    gameId: number;
    refreshKey?: unknown;
}

type VersionStatus = Awaited<ReturnType<typeof GameVersionEndpoint.getStatus>>[number];

const DLC_PREVIEW_COUNT = 3;

const STORE_NAMES: Record<string, string> = {
    STEAM: "Steam",
    GOG: "GOG",
    EPIC: "Epic Games",
};

/** Read-only table rows for the game details table. Renders nothing if no store is linked. */
export default function GameVersionDetails({gameId, refreshKey}: GameVersionDetailsProps) {
    const [statuses, setStatuses] = useState<VersionStatus[]>([]);
    const [included, setIncluded] = useState<GameIncludedDlcDto[]>([]);

    useEffect(() => {
        GameVersionEndpoint.getStatus(gameId).then(setStatuses).catch(() => setStatuses([]));
        GameDlcEndpoint.getIncluded(gameId).then(setIncluded).catch(() => setIncluded([]));
    }, [gameId, refreshKey]);

    const [dlcExpanded, setDlcExpanded] = useState(false);

    const dlcTitles = [...new Set(included.flatMap((entry) => entry.dlc.map((item) => item.title)))];

    return (
        <>
            {statuses.map((status) => {
                const installed = status.versions.find((version) => version.id === status.installedVersionId);
                const latest = status.versions.find((version) => version.id === status.latestVersionId);
                return (
                    <tr key={status.linkId}>
                        <td className="text-muted w-0 min-w-32">{STORE_NAMES[status.store] ?? status.store} version</td>
                        <td>
                            <div className="flex flex-wrap items-center gap-2">
                                <span>{installed?.label ?? "Unknown"}</span>
                                {status.edition && <Chip size="sm" variant="soft">{status.edition}</Chip>}
                                {status.updateAvailable === true &&
                                    <Chip color="warning" size="sm">Update available</Chip>}
                                {status.updateAvailable === false &&
                                    <Chip color="success" size="sm">Up to date</Chip>}
                                {latest && status.updateAvailable === true &&
                                    <span className="text-muted">Latest: {latest.label}</span>}
                            </div>
                        </td>
                    </tr>
                );
            })}
            {dlcTitles.length > 0 && (
                <tr>
                    <td className="text-muted w-0 min-w-32">Included DLC</td>
                    <td>
                        <div className="flex flex-wrap items-center gap-1">
                            {dlcTitles.slice(0, DLC_PREVIEW_COUNT).map((title) =>
                                <Chip key={title} size="sm">{title}</Chip>)}
                            {dlcTitles.length > DLC_PREVIEW_COUNT && (
                                <Button size="sm" variant="ghost" onPress={() => setDlcExpanded(!dlcExpanded)}>
                                    {dlcExpanded ? "Show less" : `+${dlcTitles.length - DLC_PREVIEW_COUNT} more`}
                                </Button>
                            )}
                        </div>
                        {dlcExpanded && (
                            <ul className="mt-2 max-h-60 list-disc overflow-y-auto pl-5">
                                {dlcTitles.slice(DLC_PREVIEW_COUNT).map((title) => <li key={title}>{title}</li>)}
                            </ul>
                        )}
                    </td>
                </tr>
            )}
        </>
    );
}
