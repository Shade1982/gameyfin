import React, {useState} from "react";
import {Button, Checkbox, Chip, Spinner, toast} from "@heroui/react";
import {GameDlcEndpoint} from "Frontend/generated/endpoints";
import IncludedDlcDto from "Frontend/generated/org/gameyfin/app/games/versions/IncludedDlcDto";
import StoreDlcDto from "Frontend/generated/org/gameyfin/app/games/versions/StoreDlcDto";
import {StagedLink} from "Frontend/components/general/versions/useGameVersionEditor";

interface IncludedDlcPickerProps {
    gameId: number;
    link: StagedLink;
    onChange: (dlc: IncludedDlcDto[]) => void;
}

/** Picks which DLC the installer(s) provided for a game include. Changes are staged until the dialog is saved. */
export default function IncludedDlcPicker({gameId, link, onChange}: IncludedDlcPickerProps) {
    const [open, setOpen] = useState(false);
    const [candidates, setCandidates] = useState<StoreDlcDto[]>();
    const [busy, setBusy] = useState(false);

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
                    <Chip key={item.dlcId} size="sm" variant="soft">{item.title}</Chip>
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

            {open && (
                <div className="flex max-h-60 flex-col gap-1 overflow-y-auto rounded-lg bg-surface p-2">
                    {busy && !candidates && <div className="flex justify-center py-2"><Spinner size="sm"/></div>}
                    {candidates?.length === 0 && (
                        <p className="text-sm text-muted">The store lists no DLC for this product.</p>
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
