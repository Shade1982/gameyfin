import React, {useEffect, useState} from "react";
import {toast} from "@heroui/react";
import {GameEndpoint} from "Frontend/generated/endpoints";
import GameMetadataLinksDto from "Frontend/generated/org/gameyfin/app/games/dto/GameMetadataLinksDto";
import InspectMetadataSourcesModal from "./InspectMetadataSourcesModal";

interface GameMetadataSourcesModalProps {
    gameId: number;
    title: string;
    isOpen: boolean;
    onOpenChange: (isOpen: boolean) => void;
}

/** Inspects the metadata sources linked to an existing game and lets the admin pin fields to a source. */
export default function GameMetadataSourcesModal({gameId, title, isOpen, onOpenChange}: GameMetadataSourcesModalProps) {
    const [links, setLinks] = useState<GameMetadataLinksDto | null>(null);
    const [overrides, setOverrides] = useState<Record<string, string>>({});
    const [isApplying, setIsApplying] = useState(false);

    useEffect(() => {
        if (!isOpen) return;
        let cancelled = false;
        setLinks(null);
        GameEndpoint.getMetadataLinks(gameId).then(result => {
            if (cancelled || !result) return;
            setLinks(result);
            setOverrides({...(result.fieldOverrides ?? {})} as Record<string, string>);
        }).catch(() => toast.danger("Failed to load the metadata links of this game"));
        return () => {
            cancelled = true;
        };
    }, [isOpen, gameId]);

    async function apply() {
        setIsApplying(true);
        try {
            await GameEndpoint.setFieldOverrides(gameId, overrides);
            toast.success("Metadata sources updated");
            onOpenChange(false);
        } catch (e) {
            toast.danger("Failed to apply the pinned fields");
        } finally {
            setIsApplying(false);
        }
    }

    if (!isOpen || links === null) return null;

    return (
        <InspectMetadataSourcesModal isOpen={isOpen}
                                     onOpenChange={onOpenChange}
                                     originalIds={links.originalIds as any}
                                     supplementIds={links.supplementIds as any}
                                     label={title}
                                     overrides={overrides}
                                     onOverridesChange={setOverrides}
                                     onApply={apply}
                                     isApplying={isApplying}/>
    );
}
