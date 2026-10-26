import {Button, Modal, Spinner, Tooltip} from "@heroui/react";
import {PushPinIcon, PushPinSlashIcon, WarningIcon} from "@phosphor-icons/react";
import React, {useEffect, useState} from "react";
import {GameEndpoint} from "Frontend/generated/endpoints";
import {useSnapshot} from "valtio/react";
import {pluginState} from "Frontend/state/PluginState";
import ExternalProviderIdDto from "Frontend/generated/org/gameyfin/app/core/plugins/dto/ExternalProviderIdDto";
import MetadataSourceInspectionDto
    from "Frontend/generated/org/gameyfin/app/games/dto/MetadataSourceInspectionDto";
import MetadataSourceStatus from "Frontend/generated/org/gameyfin/app/games/dto/MetadataSourceStatus";
import PluginIcon from "../plugin/PluginIcon";
import {METADATA_FIELD_LABELS} from "../metadata/MetadataFieldLabels";

interface InspectMetadataSourcesModalProps {
    isOpen: boolean;
    onOpenChange: (isOpen: boolean) => void;
    originalIds: Record<string, ExternalProviderIdDto>;
    supplementIds?: Record<string, ExternalProviderIdDto>;
    label: string;
    /** Field name -> key of the source the field is pinned to. Together with onOverridesChange it makes the table interactive. */
    overrides?: Record<string, string>;
    onOverridesChange?: (overrides: Record<string, string>) => void;
    /** Shows an apply button in the footer (used when editing an existing game) */
    onApply?: () => void;
    isApplying?: boolean;
}

const IMAGE_FIELDS = ["coverImage", "headerImage"];
const COUNT_ONLY_FIELDS = ["keywords", "videoUrls"];
// URLs differ between providers by nature, so these are never flagged as conflicting
const NOT_COMPARABLE_FIELDS = [...IMAGE_FIELDS, "images", "videoUrls"];
const MAX_SCREENSHOT_THUMBNAILS = 3;

function comparisonKey(field: string, values: string[]): string {
    if (field === "release") return new Date(values[0]).toDateString();
    return [...values].map(v => v.trim().toLowerCase()).sort().join("|");
}

function formatRelease(value: string): string {
    const date = new Date(value);
    return isNaN(date.getTime()) ? value : date.toLocaleDateString();
}

function Thumbnail({url, className}: { url: string, className: string }) {
    return (
        <Tooltip delay={150}>
            <Tooltip.Trigger>
                <span tabIndex={0} className="inline-block">
                    <img src={url} alt="" loading="lazy" referrerPolicy="no-referrer"
                         className={`${className} rounded object-cover bg-default`}/>
                </span>
            </Tooltip.Trigger>
            <Tooltip.Content placement="right">
                <img src={url} alt="" referrerPolicy="no-referrer"
                     className="max-h-80 max-w-md rounded object-contain"/>
            </Tooltip.Content>
        </Tooltip>
    );
}

function FieldValue({field, values}: { field: string, values: string[] }) {
    const [expanded, setExpanded] = useState(false);

    if (field === "coverImage") return <Thumbnail url={values[0]} className="h-24 w-16"/>;
    if (field === "headerImage") return <Thumbnail url={values[0]} className="h-12 w-28"/>;
    if (field === "images") {
        return (
            <div className="flex flex-row flex-wrap items-center gap-1">
                {values.slice(0, MAX_SCREENSHOT_THUMBNAILS).map(url => <Thumbnail key={url} url={url}
                                                                                  className="h-10 w-16"/>)}
                {values.length > MAX_SCREENSHOT_THUMBNAILS && (
                    <span className="text-xs text-muted">+{values.length - MAX_SCREENSHOT_THUMBNAILS}</span>
                )}
            </div>
        );
    }
    if (COUNT_ONLY_FIELDS.includes(field)) {
        return <span title={values.join(", ")}>{values.length} item{values.length === 1 ? "" : "s"}</span>;
    }
    if (field === "release") return <span>{formatRelease(values[0])}</span>;
    if (field === "summary") {
        return (
            <button type="button" className="text-left cursor-pointer" onClick={() => setExpanded(!expanded)}
                    title={expanded ? "Click to collapse" : "Click to expand"}>
                <span className={expanded ? "whitespace-pre-line" : "line-clamp-4"}>{values[0]}</span>
            </button>
        );
    }
    return <span className="break-words">{values.join(", ")}</span>;
}

export default function InspectMetadataSourcesModal({
                                                        isOpen,
                                                        onOpenChange,
                                                        originalIds,
                                                        supplementIds,
                                                        label,
                                                        overrides = {},
                                                        onOverridesChange,
                                                        onApply,
                                                        isApplying = false
                                                    }: InspectMetadataSourcesModalProps) {
    const state = useSnapshot(pluginState).state;
    const [inspection, setInspection] = useState<MetadataSourceInspectionDto | null>(null);
    const [failed, setFailed] = useState(false);
    const [onlyDifferences, setOnlyDifferences] = useState(false);

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
    const defaultWinners = inspection?.winners ?? {};
    const interactive = onOverridesChange !== undefined;

    // A pinned source only wins if it provides the field, otherwise the plugin priority decides (as in the merge)
    const winners: Record<string, string> = {...defaultWinners};
    Object.entries(overrides).forEach(([field, key]) => {
        if (sources.find(s => s.key === key)?.values?.[field]) winners[field] = key;
    });

    function pin(field: string, sourceKey: string) {
        if (!onOverridesChange) return;
        const next = {...overrides};
        // Choosing the source that wins by priority anyway is the same as removing the pin
        if (sourceKey === defaultWinners[field] || overrides[field] === sourceKey) delete next[field];
        else next[field] = sourceKey;
        onOverridesChange(next);
    }

    const pinnedCount = Object.keys(overrides).filter(field => overrides[field] === winners[field]).length;

    // A source conflicts if it provides a different value than the source that wins the field
    function conflicts(field: string, sourceKey: string): boolean {
        if (NOT_COMPARABLE_FIELDS.includes(field)) return false;
        const winnerKey = winners[field];
        const winnerValues = sources.find(s => s.key === winnerKey)?.values?.[field];
        const values = sources.find(s => s.key === sourceKey)?.values?.[field];
        if (!winnerValues || !values || sourceKey === winnerKey) return false;
        return comparisonKey(field, winnerValues) !== comparisonKey(field, values);
    }

    function isDifferent(field: string): boolean {
        const providers = sources.filter(s => s.values?.[field]);
        if (NOT_COMPARABLE_FIELDS.includes(field)) return providers.length >= 2;
        return providers.some(s => conflicts(field, s.key));
    }

    const allFields = Object.keys(METADATA_FIELD_LABELS).filter(field => sources.some(s => s.values?.[field]));
    const fields = onlyDifferences ? allFields.filter(isDifferent) : allFields;

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
                                    used in the resulting game, based on plugin priority. Values that differ from
                                    the used one are marked with a warning icon.
                                </p>
                                {interactive && (
                                    <p className="text-sm text-muted">
                                        Click the pin next to a value to use that source for the field instead of the
                                        plugin priority. {onApply
                                        ? "Pinned fields are applied when you click Apply and kept when the metadata is refreshed."
                                        : "Pinned fields are applied when you pick this result and kept when the metadata is refreshed."}
                                    </p>
                                )}
                                {interactive && pinnedCount > 0 && (
                                    <button type="button" className="text-sm underline w-fit cursor-pointer"
                                            onClick={() => onOverridesChange!({})}>
                                        Reset {pinnedCount} pinned field{pinnedCount === 1 ? "" : "s"}
                                    </button>
                                )}
                                {sources.length > 0 && (
                                    <label className="flex flex-row items-center gap-2 text-sm mt-2 cursor-pointer w-fit">
                                        <input type="checkbox" checked={onlyDifferences}
                                               onChange={(e) => setOnlyDifferences(e.target.checked)}/>
                                        Only show differences
                                    </label>
                                )}
                            </div>

                            {failed && <p className="text-danger">Failed to load the metadata sources.</p>}
                            {!failed && inspection === null && (
                                <div className="flex justify-center p-8"><Spinner/></div>
                            )}
                            {inspection !== null && sources.length === 0 &&
                                <p className="text-muted">No source was queried for this selection.</p>}

                            {sources.length > 0 && (
                                <div className="overflow-auto max-h-[60vh]">
                                    <table className="w-full text-sm border-collapse">
                                        <thead className="sticky top-0 z-10 bg-background">
                                        <tr>
                                            <th className="text-left p-2 align-bottom">Field</th>
                                            {sources.map(source => {
                                                const won = Object.values(winners).filter(w => w === source.key).length;
                                                const provided = Object.keys(source.values ?? {}).length;
                                                return (
                                                    <th key={source.key} className="text-left p-2 align-bottom">
                                                        <div className="flex flex-row items-center gap-2">
                                                            {state[source.pluginId] &&
                                                                <PluginIcon plugin={state[source.pluginId]}/>}
                                                            <div className="flex flex-col">
                                                                <span>
                                                                    {state[source.pluginId]?.name ?? source.pluginId}
                                                                    {source.baseGame ? " (base game)" : ""}
                                                                </span>
                                                                {source.status === MetadataSourceStatus.OK ? (
                                                                    <span className="text-xs font-normal text-muted">
                                                                        {provided} provided, {won} used
                                                                    </span>
                                                                ) : (
                                                                    <span
                                                                        className="text-xs font-normal text-danger"
                                                                        title={source.error ?? undefined}>
                                                                        {source.status === MetadataSourceStatus.ERROR ? "Error" : "No data"}
                                                                    </span>
                                                                )}
                                                            </div>
                                                        </div>
                                                    </th>
                                                );
                                            })}
                                        </tr>
                                        </thead>
                                        <tbody>
                                        {fields.length === 0 && (
                                            <tr>
                                                <td colSpan={sources.length + 1} className="p-4 text-center text-muted">
                                                    {allFields.length === 0 ? "No source returned any metadata." : "All sources agree."}
                                                </td>
                                            </tr>
                                        )}
                                        {fields.map(field => (
                                            <tr key={field} className="border-t border-divider align-top">
                                                <td className="p-2 font-medium whitespace-nowrap">
                                                    {METADATA_FIELD_LABELS[field]}
                                                </td>
                                                {sources.map(source => {
                                                    const values = source.values?.[field];
                                                    const isWinner = winners[field] === source.key;
                                                    const isPinned = overrides[field] === source.key && isWinner;
                                                    const differs = conflicts(field, source.key);
                                                    return (
                                                        <td key={source.key}
                                                            className={`p-2 max-w-72 ${isWinner ? "bg-success/15" : differs ? "bg-warning/10" : ""}`}>
                                                            {values ? (
                                                                <div className="flex flex-row items-start gap-1">
                                                                    {differs && (
                                                                        <span title="Differs from the used value"
                                                                              className="text-warning pt-0.5">
                                                                            <WarningIcon size={14} weight="fill"/>
                                                                        </span>
                                                                    )}
                                                                    <FieldValue field={field} values={values}/>
                                                                    {interactive && (isPinned || !isWinner) && (
                                                                        <button type="button"
                                                                                className={`ml-auto shrink-0 cursor-pointer pt-0.5 ${isPinned ? "text-success" : "text-muted hover:text-foreground"}`}
                                                                                title={isPinned ? "Pinned. Click to use the plugin priority again" : `Use this source for ${METADATA_FIELD_LABELS[field]}`}
                                                                                onClick={() => pin(field, source.key)}>
                                                                            {isPinned ?
                                                                                <PushPinSlashIcon size={16}
                                                                                                  weight="fill"/> :
                                                                                <PushPinIcon size={16}/>}
                                                                        </button>
                                                                    )}
                                                                </div>
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
                        {onApply && (
                            <Modal.Footer>
                                <Button variant="tertiary" onPress={() => onOpenChange(false)}>Cancel</Button>
                                <Button variant="primary" isPending={isApplying} onPress={onApply}>Apply</Button>
                            </Modal.Footer>
                        )}
                    </Modal.Dialog>
                </Modal.Container>
            </Modal.Backdrop>
        </Modal>
    );
}
