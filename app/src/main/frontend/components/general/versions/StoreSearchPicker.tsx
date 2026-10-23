import React, {useCallback, useEffect, useRef, useState} from "react";
import {Button, Input, Label, ListBox, Select, Spinner, TextField} from "@heroui/react";
import {GameStoreSearchEndpoint} from "Frontend/generated/endpoints";
import GameStore from "Frontend/generated/org/gameyfin/app/games/versions/GameStore";

export const STORE_NAMES: Record<GameStore, string> = {
    [GameStore.STEAM]: "Steam",
    [GameStore.GOG]: "GOG",
    [GameStore.EPIC]: "Epic Games",
};

const MANUAL_HINTS: Record<GameStore, string> = {
    [GameStore.STEAM]: "Steam app ID or store URL",
    [GameStore.GOG]: "GOG product ID or GOGDB URL",
    [GameStore.EPIC]: "Epic offer ID or egdata.app URL",
};

const ID_PATTERNS: Record<GameStore, { id: RegExp; urls: RegExp[] }> = {
    [GameStore.STEAM]: {
        id: /^\d{1,20}$/,
        urls: [/store\.steampowered\.com\/app\/(\d{1,20})/, /steamdb\.info\/app\/(\d{1,20})/],
    },
    [GameStore.GOG]: {id: /^\d{1,20}$/, urls: [/gogdb\.org\/product\/(\d{1,20})/]},
    [GameStore.EPIC]: {id: /^[0-9a-f]{32}$/, urls: [/egdata\.app\/offers\/([0-9a-fA-F]{32})/]},
};

/** Mirrors the server-side parsing, which validates again when the link is saved. */
function parseStoreId(store: GameStore, input: string): string | undefined {
    const trimmed = input.trim();
    const {id, urls} = ID_PATTERNS[store];
    const candidate = urls.map((url) => url.exec(trimmed)?.[1]).find(Boolean) ?? trimmed;
    const normalized = store === GameStore.EPIC ? candidate.toLowerCase() : candidate;
    return id.test(normalized) ? normalized : undefined;
}

type SearchResult = Awaited<ReturnType<typeof GameStoreSearchEndpoint.search>>[number];

interface StoreSearchPickerProps {
    initialQuery: string;
    /** `${store}:${id}` of products that are already linked */
    linkedKeys: Set<string>;
    onPick: (store: GameStore, storeAppId: string, title?: string) => void;
    onClose: () => void;
}

export default function StoreSearchPicker({initialQuery, linkedKeys, onPick, onClose}: StoreSearchPickerProps) {
    const [store, setStore] = useState<GameStore>(GameStore.STEAM);
    const [query, setQuery] = useState(initialQuery);
    const [results, setResults] = useState<SearchResult[]>();
    const [searching, setSearching] = useState(false);
    const [error, setError] = useState<string>();
    const [manual, setManual] = useState(false);
    const [manualInput, setManualInput] = useState("");
    const latestRequest = useRef(0);

    const search = useCallback(async (searchStore: GameStore, term: string) => {
        const request = ++latestRequest.current;
        setSearching(true);
        setError(undefined);
        try {
            const found = await GameStoreSearchEndpoint.search(searchStore, term);
            if (request === latestRequest.current) setResults(found);
        } catch (e) {
            if (request === latestRequest.current) {
                setResults(undefined);
                setError(`Could not search ${STORE_NAMES[searchStore]}. You can enter the ID manually instead.`);
            }
        } finally {
            if (request === latestRequest.current) setSearching(false);
        }
    }, []);

    // Searching with the game's title right away is what makes picking a product a one-click action
    useEffect(() => {
        void search(store, initialQuery);
    }, [store, initialQuery, search]);

    const manualId = parseStoreId(store, manualInput);

    function addManual() {
        if (!manualId) return;
        onPick(store, manualId);
        setManualInput("");
    }

    return (
        <div className="flex flex-col gap-3 rounded-xl border border-default-200 p-3">
            <div className="flex flex-wrap items-end gap-2">
                <Select
                    aria-label="Store"
                    className="w-40"
                    value={store}
                    onChange={(value) => setStore(value as GameStore)}
                >
                    <Select.Trigger>
                        <Select.Value/>
                        <Select.Indicator/>
                    </Select.Trigger>
                    <Select.Popover>
                        <ListBox>
                            {Object.values(GameStore).map((option) => (
                                <ListBox.Item key={option} id={option} textValue={STORE_NAMES[option]}>
                                    {STORE_NAMES[option]}
                                    <ListBox.ItemIndicator/>
                                </ListBox.Item>
                            ))}
                        </ListBox>
                    </Select.Popover>
                </Select>
                <TextField className="min-w-48 flex-1" value={query} onChange={setQuery}>
                    <Label className="sr-only">Search term</Label>
                    <Input
                        variant="secondary"
                        placeholder="Search by game title"
                        onKeyDown={(e) => {
                            if (e.key === "Enter") {
                                e.preventDefault();
                                void search(store, query);
                            }
                        }}
                    />
                </TextField>
                <Button size="sm" variant="outline" isDisabled={searching || query.trim().length < 2}
                        onPress={() => void search(store, query)}>
                    Search
                </Button>
                <Button size="sm" variant="ghost" onPress={onClose}>Done</Button>
            </div>

            {searching && <div className="flex justify-center py-2"><Spinner size="sm"/></div>}
            {error && <p className="text-sm text-danger">{error}</p>}
            {!searching && results?.length === 0 &&
                <p className="text-sm text-muted">No results on {STORE_NAMES[store]}. Try another search term.</p>}
            {!searching && results && results.length > 0 && (
                <ul className="flex max-h-56 flex-col gap-1 overflow-y-auto">
                    {results.map((result) => {
                        const linked = linkedKeys.has(`${store}:${result.id}`);
                        return (
                            <li key={result.id} className="flex items-center gap-3 rounded-lg p-1 hover:bg-default-100">
                                {result.imageUrl
                                    ? <img src={result.imageUrl} alt="" loading="lazy" referrerPolicy="no-referrer"
                                           className="h-12 w-9 rounded object-cover"
                                           onError={(event) => {
                                               event.currentTarget.style.visibility = "hidden";
                                           }}/>
                                    : <div className="h-12 w-9 rounded bg-default-200"/>}
                                <div className="flex min-w-0 flex-1 flex-col">
                                    <span className="truncate">{result.title}</span>
                                    <span className="truncate text-xs text-muted">
                                        {[result.subtitle, `ID ${result.id}`].filter(Boolean).join(" · ")}
                                    </span>
                                </div>
                                <Button size="sm" variant="outline" isDisabled={linked}
                                        onPress={() => onPick(store, result.id, result.title)}>
                                    {linked ? "Added" : "Add"}
                                </Button>
                            </li>
                        );
                    })}
                </ul>
            )}

            <div className="border-t border-default-200 pt-2">
                {manual ? (
                    <div className="flex flex-wrap items-end gap-2">
                        <TextField className="min-w-48 flex-1" value={manualInput} onChange={setManualInput}>
                            <Label className="sr-only">Product ID or URL</Label>
                            <Input
                                variant="secondary"
                                placeholder={MANUAL_HINTS[store]}
                                onKeyDown={(e) => {
                                    if (e.key === "Enter") {
                                        e.preventDefault();
                                        addManual();
                                    }
                                }}
                            />
                        </TextField>
                        <Button size="sm" isDisabled={!manualId || linkedKeys.has(`${store}:${manualId}`)}
                                onPress={addManual}>
                            Add
                        </Button>
                        {manualInput.trim() && !manualId &&
                            <p className="w-full text-xs text-danger">Not a valid {STORE_NAMES[store]} ID or URL</p>}
                    </div>
                ) : (
                    <Button size="sm" variant="ghost" onPress={() => setManual(true)}>
                        Can't find it? Enter an ID or URL manually
                    </Button>
                )}
            </div>
        </div>
    );
}
