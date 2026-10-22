import React, {useState} from "react";
import {Button, Chip, ListBox, Select, Spinner} from "@heroui/react";
import StoreSearchPicker, {STORE_NAMES} from "Frontend/components/general/versions/StoreSearchPicker";
import {GameVersionEditorState} from "Frontend/components/general/versions/useGameVersionEditor";

interface GameVersionEditorProps {
    gameTitle: string;
    editor: GameVersionEditorState;
}

/** Edits are staged in [editor] and written when the metadata dialog is saved. */
export default function GameVersionEditor({gameTitle, editor}: GameVersionEditorProps) {
    const [adding, setAdding] = useState(false);
    const {links, loading, pendingKey} = editor;
    const linkedKeys = new Set(links.map((link) => `${link.store}:${link.storeAppId}`));

    if (loading) return <div className="flex justify-center py-3"><Spinner size="sm"/></div>;

    return (
        <section className="flex flex-col gap-3">
            {links.length === 0 && !adding && (
                <p className="text-sm text-muted">No store is linked to this game yet.</p>
            )}

            {links.map((link) => {
                const busy = pendingKey === link.key;
                const isNew = link.linkId === undefined;
                return (
                    <div key={link.key} className="flex flex-col gap-2 rounded-xl bg-surface-secondary p-3">
                        <div className="flex flex-wrap items-center gap-2">
                            <span className="font-medium">{STORE_NAMES[link.store]}</span>
                            <span className="text-sm text-muted">
                                {link.title ? `${link.title} · ` : ""}ID {link.storeAppId}
                            </span>
                            {isNew && <Chip size="sm" variant="soft">New</Chip>}
                            {!isNew && links.length > 1 && link.tracked &&
                                <Chip size="sm" variant="soft">Primary</Chip>}
                        </div>

                        {link.loadingVersions ? (
                            <div className="flex items-center gap-2 text-sm text-muted">
                                <Spinner size="sm"/> Fetching versions…
                            </div>
                        ) : (
                            <Select
                                aria-label={`Installed ${STORE_NAMES[link.store]} version`}
                                value={link.installedVersionId?.toString() ?? "none"}
                                isDisabled={busy || link.versions.length === 0}
                                onChange={(value) => {
                                    const selected = value?.toString() ?? "none";
                                    editor.setInstalledVersion(link.key, selected === "none" ? undefined : Number(selected));
                                }}
                            >
                                <Select.Trigger>
                                    <Select.Value/>
                                    <Select.Indicator/>
                                </Select.Trigger>
                                <Select.Popover>
                                    <ListBox>
                                        <ListBox.Item id="none" textValue="Not set">
                                            Installed version: not set
                                            <ListBox.ItemIndicator/>
                                        </ListBox.Item>
                                        {link.versions.map((version) => (
                                            <ListBox.Item key={version.id} id={version.id.toString()}
                                                          textValue={version.label}>
                                                {version.label}{version.branch ? ` (${version.branch})` : ""}
                                                <ListBox.ItemIndicator/>
                                            </ListBox.Item>
                                        ))}
                                    </ListBox>
                                </Select.Popover>
                            </Select>
                        )}

                        {!link.loadingVersions && link.versions.length === 0 && (
                            <p className="text-sm text-muted">
                                No versions are available yet. Use Refresh to try again.
                            </p>
                        )}

                        <div className="flex flex-wrap items-center gap-2">
                            <Button size="sm" variant="outline" isPending={busy} isDisabled={busy || link.loadingVersions}
                                    onPress={() => void editor.detect(link.key)}>
                                Detect
                            </Button>
                            <Button size="sm" variant="outline" isDisabled={busy || link.loadingVersions}
                                    onPress={() => isNew ? editor.refreshPreview(link.key) : void editor.refresh(link.key)}>
                                Refresh
                            </Button>
                            {!isNew && links.length > 1 && !link.tracked && (
                                <Button size="sm" variant="ghost" isDisabled={busy}
                                        onPress={() => editor.makePrimary(link.key)}>
                                    Make primary
                                </Button>
                            )}
                            <Button size="sm" variant="ghost" isDisabled={busy}
                                    onPress={() => editor.removeLink(link.key)}>
                                Remove
                            </Button>
                            {link.versionsCheckedAt && (
                                <span className="ml-auto text-xs text-muted">
                                    Checked {new Date(link.versionsCheckedAt).toLocaleString()}
                                </span>
                            )}
                        </div>
                    </div>
                );
            })}

            {adding ? (
                <StoreSearchPicker
                    initialQuery={gameTitle}
                    linkedKeys={linkedKeys}
                    onPick={editor.addLink}
                    onClose={() => setAdding(false)}
                />
            ) : (
                <div>
                    <Button size="sm" variant="outline" onPress={() => setAdding(true)}>Add store</Button>
                </div>
            )}
        </section>
    );
}
