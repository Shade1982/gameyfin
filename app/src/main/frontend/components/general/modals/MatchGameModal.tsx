import {Button, Input, Modal, Spinner, Table, Tooltip} from "@heroui/react";
import React, {useEffect, useState} from "react";
import {ArrowRightIcon, LinkIcon, MagnifyingGlassIcon, XIcon} from "@phosphor-icons/react";
import {GameEndpoint} from "Frontend/generated/endpoints";
import GameSearchResultDto from "Frontend/generated/org/gameyfin/app/games/dto/GameSearchResultDto";
import PluginIcon from "../plugin/PluginIcon";
import {useSnapshot} from "valtio/react";
import {pluginState} from "Frontend/state/PluginState";
import {libraryState} from "Frontend/state/LibraryState";
import LibraryAdminDto from "Frontend/generated/org/gameyfin/app/libraries/dto/LibraryAdminDto";

interface MatchGameModalProps {
    path: string;
    libraryId: number;
    replaceGameId?: number;
    initialSearchTerm: string;
    isOpen: boolean;
    onOpenChange: (isOpen: boolean) => void;
}

export default function MatchGameModal({
                                           path,
                                           libraryId,
                                           replaceGameId,
                                           initialSearchTerm,
                                           isOpen,
                                           onOpenChange
                                       }: MatchGameModalProps) {
    const [searchTerm, setSearchTerm] = useState("");
    const [searchResults, setSearchResults] = useState<GameSearchResultDto[]>([]);
    const [isSearching, setIsSearching] = useState(false);
    const [isMatching, setIsMatching] = useState<string | null>(null);
    const [combineBase, setCombineBase] = useState<GameSearchResultDto | null>(null);

    const state = useSnapshot(pluginState).state;
    const librariesState = useSnapshot(libraryState).state;

    useEffect(() => {
        setSearchTerm(initialSearchTerm);
        setSearchResults([]);
        setCombineBase(null);
    }, [isOpen]);

    async function matchGame(result: GameSearchResultDto) {
        // Combining unions the provider IDs; the backend merges them by plugin priority as usual
        const originalIds = combineBase ? {...combineBase.originalIds, ...result.originalIds} : result.originalIds;
        await GameEndpoint.matchManually(originalIds, path, libraryId, replaceGameId);
    }

    // Each provider can only supply one ID, so results sharing a provider cannot be combined
    function sharesProvider(result: GameSearchResultDto) {
        return combineBase !== null && Object.keys(result.originalIds).some(key => key in combineBase.originalIds);
    }

    function label(result: GameSearchResultDto) {
        return `${result.title} (${result.release ? new Date(result.release).getFullYear() : "unknown"})`;
    }

    async function search() {
        setIsSearching(true);
        const results = await GameEndpoint.getPotentialMatches(searchTerm, (librariesState[libraryId] as LibraryAdminDto).platforms);
        setSearchResults(results);
        setIsSearching(false);
    }

    return (
        <Modal>
            <Modal.Backdrop isOpen={isOpen} onOpenChange={onOpenChange}
                            isDismissable={!isSearching && !isMatching}>
                <Modal.Container size="lg" className="max-w-5xl">
                    <Modal.Dialog>
                        {({close}) => (
                            <Modal.Body className="my-4">
                                <div className="flex flex-col items-center">
                                    <pre>{path}</pre>
                                </div>
                                <div className="flex flex-row gap-2 mb-4">
                                    <Input value={searchTerm}
                                           onChange={(e) => setSearchTerm(e.target.value)}
                                           onKeyDown={async (e) => {
                                               if (e.key === "Enter") {
                                                   e.preventDefault();
                                                   await search();
                                               }
                                           }}
                                           className="flex-1"
                                    />
                                    <Button isIconOnly onPress={search} variant="primary" isPending={isSearching}>
                                        {isSearching ? <Spinner size="sm" color="current"/> : <MagnifyingGlassIcon/>}
                                    </Button>
                                </div>

                                {combineBase && (
                                    <div className="flex flex-row items-center gap-2 mb-4">
                                        <p className="flex-1 text-sm">
                                            Combining with <b>{label(combineBase)}</b>. Pick the second result that
                                            describes the same game. Results from the same source are disabled.
                                        </p>
                                        <Button size="sm" variant="tertiary" isDisabled={isMatching !== null}
                                                onPress={() => setCombineBase(null)}>
                                            <XIcon/> Cancel
                                        </Button>
                                    </div>
                                )}

                                <div>
                                    <Table variant="secondary" className="h-80">
                                        <Table.ScrollContainer className="h-80">
                                            <Table.Content>
                                                <Table.Header className="sticky top-0 z-10 bg-background">
                                                    <Table.Column id="title" isRowHeader>Title & Release</Table.Column>
                                                    <Table.Column id="developers">Developer(s)</Table.Column>
                                                    <Table.Column id="publishers">Publisher(s)</Table.Column>
                                                    <Table.Column id="sources">Sources</Table.Column>
                                                    <Table.Column id="actions"> </Table.Column>
                                                </Table.Header>
                                                <Table.Body renderEmptyState={() => <p
                                                    className="text-center text-muted p-4">Your filter did not match any
                                                    games.</p>}
                                                            items={searchResults}>
                                                    {(item) => (
                                                        <Table.Row key={item.id}>
                                                            <Table.Cell>
                                                                {item.title} ({item.release ? new Date(item.release).getFullYear() : "unknown"})
                                                            </Table.Cell>
                                                            <Table.Cell>
                                                                <div className="flex flex-col">
                                                                    {item.developers ? item.developers.map(
                                                                        developer => <p>{developer}</p>
                                                                    ) : "unknown"}
                                                                </div>
                                                            </Table.Cell>
                                                            <Table.Cell>
                                                                <div className="flex flex-col">
                                                                    {item.publishers ? item.publishers.map(
                                                                        publisher => <p>{publisher}</p>
                                                                    ) : "unknown"}
                                                                </div>
                                                            </Table.Cell>
                                                            <Table.Cell>
                                                                <div className="flex flex-row gap-2">
                                                                    {Object.values(item.originalIds).map(
                                                                        originalId => <PluginIcon
                                                                            plugin={state[originalId.pluginId]}/>
                                                                    )}
                                                                </div>
                                                            </Table.Cell>
                                                            <Table.Cell>
                                                                <div className="flex flex-row gap-1">
                                                                {combineBase === null && (
                                                                    <Tooltip>
                                                                        <Tooltip.Trigger>
                                                                            <Button isIconOnly size="sm"
                                                                                    variant="tertiary"
                                                                                    isDisabled={isMatching !== null}
                                                                                    onPress={() => setCombineBase(item)}>
                                                                                <LinkIcon/>
                                                                            </Button>
                                                                        </Tooltip.Trigger>
                                                                        <Tooltip.Content placement="bottom">Combine
                                                                            with another result</Tooltip.Content>
                                                                    </Tooltip>
                                                                )}
                                                                <Tooltip>
                                                                    <Tooltip.Trigger>
                                                                        <Button isIconOnly size="sm"
                                                                                isDisabled={isMatching !== null || item.id === combineBase?.id || sharesProvider(item)}
                                                                                isPending={isMatching === item.id}
                                                                                onPress={async () => {
                                                                                    setIsMatching(item.id);
                                                                                    await matchGame(item);
                                                                                    setIsMatching(null);
                                                                                    close();
                                                                                }}>
                                                                            {isMatching === item.id ?
                                                                                <Spinner size="sm" color="current"/> :
                                                                                <ArrowRightIcon/>}
                                                                        </Button>
                                                                    </Tooltip.Trigger>
                                                                    <Tooltip.Content placement="bottom">
                                                                        {combineBase ? "Combine with this result" : "Pick this result"}
                                                                    </Tooltip.Content>
                                                                </Tooltip>
                                                                </div>
                                                            </Table.Cell>
                                                        </Table.Row>
                                                    )}
                                                </Table.Body>
                                            </Table.Content>
                                        </Table.ScrollContainer>
                                    </Table>
                                </div>
                            </Modal.Body>
                        )}
                    </Modal.Dialog>
                </Modal.Container>
            </Modal.Backdrop>
        </Modal>
    );
}