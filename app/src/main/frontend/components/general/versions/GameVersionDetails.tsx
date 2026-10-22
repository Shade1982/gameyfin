import React, {useEffect, useState} from "react";
import {Chip} from "@heroui/react";
import {GameVersionEndpoint} from "Frontend/generated/endpoints";

interface GameVersionDetailsProps {
    gameId: number;
    refreshKey?: unknown;
}

type VersionStatus = Awaited<ReturnType<typeof GameVersionEndpoint.getStatus>>[number];

const STORE_NAMES: Record<string, string> = {
    STEAM: "Steam",
    GOG: "GOG",
    EPIC: "Epic Games",
};

/** Read-only table rows for the game details table. Renders nothing if no store is linked. */
export default function GameVersionDetails({gameId, refreshKey}: GameVersionDetailsProps) {
    const [statuses, setStatuses] = useState<VersionStatus[]>([]);

    useEffect(() => {
        GameVersionEndpoint.getStatus(gameId).then(setStatuses).catch(() => setStatuses([]));
    }, [gameId, refreshKey]);

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
        </>
    );
}
