import React, {useState} from "react";
import {Button, Checkbox, Chip, Input, Label, Spinner, TextField, toast} from "@heroui/react";
import {GameDlcEndpoint} from "Frontend/generated/endpoints";
import IncludedDlcDto from "Frontend/generated/org/gameyfin/app/games/versions/IncludedDlcDto";
import StoreDlcDto from "Frontend/generated/org/gameyfin/app/games/versions/StoreDlcDto";
import {StagedLink} from "Frontend/components/general/versions/useGameVersionEditor";

interface IncludedDlcPickerProps {
    gameId: number;
    link: StagedLink;
    onChange: (dlc: IncludedDlcDto[]) => void;
}

/** Custom DLC IDs are derived from the title, so adding the same name twice does not create a duplicate. */
function customDlcId(title: string) {
    const slug = title.toLowerCase().normalize("NFKD").replace(/[^a-z0-9]+/g, "-").replace(/^-+|-+$/g, "").slice(0, 100);
    return slug ? `custom:${slug}` : undefined;
}

/** Picks which DLC the installer(s) provided for a game include. Changes are staged until the dialog is saved. */
export default function IncludedDlcPicker({gameId, link, onChange}: IncludedDlcPickerProps) {
    const [open, setOpen] = useState(false);
    const [candidates, setCandidates] = useState<StoreDlcDto[]>();
    const [busy, setBusy] = useState(false);
    const [customTitle, setCustomTitle] = useState("");

    const selectedIds = new Set(link.dlc.map((item) => item.dlcId));

    async function loadCandidates(): Promise<void> {
        setBusy(true);
        try {
            setCandidates(await GameDlcEndpoint.getCandidates(link.store, link.storeAppId));
        } catch (error) {
            toast.danger("Could not load DLC", {description: error instanceof Error ? error.message : "Please try again."});
        } finally {
            setBusy(false);
        }
    }

    async function toggleOpen() {
        const next = !open;
        setOpen(next);
        if (next && !candidates) await loadCandidates();
    }

    function toggle(dlc: StoreDlcDto, selected: boolean) {
        onChange(selected
            ? [...link.dlc, {dlcId: dlc.dlcId, title: dlc.title, detected: false}]
            : link.dlc.filter((item) => item.dlcId !== dlc.dlcId));
    }

    function addCustom() {
        const title = customTitle.trim();
        const dlcId = customDlcId(title);
        if (!dlcId || title.length > 255) return;
        if (!selectedIds.has(dlcId)) onChange([...link.dlc, {dlcId, title, detected: false}]);
        setCustomTitle("");
    }

    function selectAll(dlc: StoreDlcDto[]) {
        const missing = dlc.filter((item) => !selectedIds.has(item.dlcId))
            .map((item) => ({dlcId: item.dlcId, title: item.title, detected: false}));
        onChange([...link.dlc, ...missing]);
    }

    async function detect() {
        setBusy(true);
        try {
            const found = await GameDlcEndpoint.detectIncluded(gameId, link.store, link.storeAppId);
            if (!candidates) await loadCandidates();
            // Manual picks are kept, detected entries are replaced by the new result
            const manual = link.dlc.filter((item) => !item.detected);
            const detected = found
                .filter((dlc) => !manual.some((item) => item.dlcId === dlc.dlcId))
                .map((dlc) => ({dlcId: dlc.dlcId, title: dlc.title, detected: true}));
            onChange([...manual, ...detected]);
            if (found.length > 0) {
                toast.success("DLC detected", {description: found.map((dlc) => dlc.title).join(", ")});
            } else {
                toast.info("No DLC detected", {description: "No installer or folder name matches a DLC of this product."});
            }
        } catch (error) {
            toast.danger("Could not detect DLC", {description: error instanceof Error ? error.message : "Please try again."});
        } finally {
            setBusy(false);
        }
    }

    return (
        <div className="flex flex-col gap-2">
            <div className="flex flex-wrap items-center gap-2">
                <span className="text-sm text-muted">Included DLC:</span>
                {link.dlc.length === 0 && <span className="text-sm text-muted">none selected</span>}
                {link.dlc.map((item) => (
                    <Chip key={item.dlcId} size="sm" variant="soft">
                        <span className="flex items-center gap-1">
                            {item.title}
                            <button type="button" className="text-muted hover:text-foreground"
                                    aria-label={`Remove ${item.title}`}
                                    onClick={() => onChange(link.dlc.filter((entry) => entry.dlcId !== item.dlcId))}>
                                ×
                            </button>
                        </span>
                    </Chip>
                ))}
            </div>
            <div className="flex flex-wrap items-center gap-2">
                <Button size="sm" variant="outline" isDisabled={busy} onPress={() => void toggleOpen()}>
                    {open ? "Hide DLC list" : "Choose DLC"}
                </Button>
                <Button size="sm" variant="outline" isPending={busy} isDisabled={busy} onPress={() => void detect()}>
                    Detect DLC
                </Button>
            </div>

            <div className="flex flex-wrap items-end gap-2">
                <TextField className="min-w-48 flex-1" value={customTitle} onChange={setCustomTitle}>
                    <Label className="sr-only">Custom DLC name</Label>
                    <Input
                        variant="secondary"
                        placeholder="Add a DLC the store does not list"
                        maxLength={255}
                        onKeyDown={(event) => {
                            if (event.key === "Enter") {
                                event.preventDefault();
                                addCustom();
                            }
                        }}
                    />
                </TextField>
                <Button size="sm" variant="outline" isDisabled={!customDlcId(customTitle.trim())} onPress={addCustom}>
                    Add
                </Button>
            </div>

            {open && (
                <div className="flex max-h-60 flex-col gap-1 overflow-y-auto rounded-lg bg-surface p-2">
                    {busy && !candidates && <div className="flex justify-center py-2"><Spinner size="sm"/></div>}
                    {candidates?.length === 0 && (
                        <p className="text-sm text-muted">The store lists no DLC for this product.</p>
                    )}
                    {candidates && candidates.length > 1 && (
                        <div className="flex gap-2 pb-1">
                            <Button size="sm" variant="ghost" onPress={() => selectAll(candidates)}>Select all</Button>
                            <Button size="sm" variant="ghost" isDisabled={link.dlc.length === 0}
                                    onPress={() => onChange([])}>
                                Clear
                            </Button>
                        </div>
                    )}
                    {candidates?.map((dlc) => (
                        <Checkbox key={dlc.dlcId} isSelected={selectedIds.has(dlc.dlcId)}
                                  onChange={(selected: boolean) => toggle(dlc, selected)}>
                            <Checkbox.Content>
                                <div className="flex flex-row items-center gap-2">
                                    <Checkbox.Control>
                                        <Checkbox.Indicator/>
                                    </Checkbox.Control>
                                    <span className="text-sm">{dlc.title}</span>
                                </div>
                            </Checkbox.Content>
                        </Checkbox>
                    ))}
                </div>
            )}
        </div>
    );
}
