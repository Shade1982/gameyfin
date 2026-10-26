import {Modal, Spinner} from "@heroui/react";
import React, {useEffect, useState} from "react";
import {GameEndpoint} from "Frontend/generated/endpoints";
import {useSnapshot} from "valtio/react";
import {pluginState} from "Frontend/state/PluginState";
import ExternalProviderIdDto from "Frontend/generated/org/gameyfin/app/core/plugins/dto/ExternalProviderIdDto";
import MetadataSourceInspectionDto
    from "Frontend/generated/org/gameyfin/app/games/dto/MetadataSourceInspectionDto";
import PluginIcon from "../plugin/PluginIcon";

interface InspectMetadataSourcesModalProps {
    isOpen: boolean;
    onOpenChange: (isOpen: boolean) => void;
    originalIds: Record<string, ExternalProviderIdDto>;
    supplementIds?: Record<string, ExternalProviderIdDto>;
    label: string;
}

const FIELD_LABELS: Record<string, string> = {
    title: "Title",
    platforms: "Platforms",
    release: "Release",
    summary: "Summary",
    developers: "Developers",
    publishers: "Publishers",
    userRating: "User rating",
    criticRating: "Critic rating",
    genres: "Genres",
    themes: "Themes",
    keywords: "Keywords",
    features: "Features",
    perspectives: "Perspectives",
    coverImage: "Cover",
    headerImage: "Header",
    images: "Screenshots",
    videoUrls: "Videos"
};

const COUNT_ONLY_FIELDS = ["images", "videoUrls", "keywords"];

function formatValue(field: string, values: string[]): string {
    if (field === "release") {
        const date = new Date(values[0]);
        return isNaN(date.getTime()) ? values[0] : date.toLocaleDateString();
    }
    if (COUNT_ONLY_FIELDS.includes(field)) return `${values.length} item${values.length === 1 ? "" : "s"}`;
    if (field === "coverImage" || field === "headerImage") return "available";
    return values.join(", ");
}

export default function InspectMetadataSourcesModal({
                                                        isOpen,
                                                        onOpenChange,
                                                        originalIds,
                                                        supplementIds,
                                                        label
                                                    }: InspectMetadataSourcesModalProps) {
    const state = useSnapshot(pluginState).state;
    const [inspection, setInspection] = useState<MetadataSourceInspectionDto | null>(null);
    const [failed, setFailed] = useState(false);

    useEffect(() => {
        if (!isOpen) return;
        let cancelled = false;
        setInspection(null);
        setFailed(false);
        GameEndpoint.inspectMetadataSources(originalIds, supplementIds)
            .then(result => {
                if (!cancelled) setInspection(result ?? null);
            })
            .catch(() => {
                if (!cancelled) setFailed(true);
            });
        return () => {
            cancelled = true;
        };
    }, [isOpen]);

    const sources = inspection?.sources ?? [];
    const winners = inspection?.winners ?? {};
    const fields = Object.keys(FIELD_LABELS).filter(field => sources.some(s => s.values?.[field]));

    return (
        <Modal>
            <Modal.Backdrop isOpen={isOpen} onOpenChange={onOpenChange}>
                <Modal.Container size="lg" className="max-w-6xl">
                    <Modal.Dialog>
                        <Modal.Body className="my-4">
                            <div className="flex flex-col gap-1 mb-4">
                                <h2 className="text-lg font-semibold">Metadata sources</h2>
                                <p className="text-sm text-muted">
                                    What each source returns for <b>{label}</b>. Highlighted values are the ones
                                    used in the resulting game, based on plugin priority.
                                </p>
                            </div>

                            {failed && <p className="text-danger">Failed to load the metadata sources.</p>}
                            {!failed && inspection === null && (
                                <div className="flex justify-center p-8"><Spinner/></div>
                            )}
                            {inspection !== null && sources.length === 0 &&
                                <p className="text-muted">No source returned any metadata.</p>}

                            {sources.length > 0 && (
                                <div className="overflow-auto max-h-[60vh]">
                                    <table className="w-full text-sm border-collapse">
                                        <thead className="sticky top-0 z-10 bg-background">
                                        <tr>
                                            <th className="text-left p-2 align-bottom">Field</th>
                                            {sources.map(source => {
                                                const won = Object.values(winners).filter(w => w === source.key).length;
                                                return (
                                                    <th key={source.key} className="text-left p-2 align-bottom">
                                                        <div className="flex flex-row items-center gap-2">
                                                            {state[source.pluginId] && <PluginIcon plugin={state[source.pluginId]}/>}
                                                            <div className="flex flex-col">
                                                                <span>
                                                                    {state[source.pluginId]?.name ?? source.pluginId}
                                                                    {source.isBaseGame ? " (base game)" : ""}
                                                                </span>
                                                                <span className="text-xs font-normal text-muted">
                                                                    {Object.keys(source.values ?? {}).length} provided, {won} used
                                                                </span>
                                                            </div>
                                                        </div>
                                                    </th>
                                                );
                                            })}
                                        </tr>
                                        </thead>
                                        <tbody>
                                        {fields.map(field => (
                                            <tr key={field} className="border-t border-divider align-top">
                                                <td className="p-2 font-medium whitespace-nowrap">
                                                    {FIELD_LABELS[field]}
                                                </td>
                                                {sources.map(source => {
                                                    const values = source.values?.[field];
                                                    const isWinner = winners[field] === source.key;
                                                    return (
                                                        <td key={source.key}
                                                            className={`p-2 max-w-72 ${isWinner ? "bg-success/15" : ""}`}>
                                                            {values ? (
                                                                <span className="line-clamp-4 break-words"
                                                                      title={values.join(", ")}>
                                                                    {formatValue(field, values)}
                                                                </span>
                                                            ) : <span className="text-muted">-</span>}
                                                        </td>
                                                    );
                                                })}
                                            </tr>
                                        ))}
                                        </tbody>
                                    </table>
                                </div>
                            )}
                        </Modal.Body>
                    </Modal.Dialog>
                </Modal.Container>
            </Modal.Backdrop>
        </Modal>
    );
}
