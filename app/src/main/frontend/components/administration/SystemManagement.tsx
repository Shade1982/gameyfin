import React, {useState} from "react";
import withConfigPage from "Frontend/components/administration/withConfigPage";
import {Button} from "@heroui/react";
import Section from "Frontend/components/general/Section";
import CheckboxInput from "Frontend/components/general/input/CheckboxInput";
import TextAreaInput from "Frontend/components/general/input/TextAreaInput";
import BackupManagement from "Frontend/components/administration/BackupManagement";
import DatabaseManagement from "Frontend/components/administration/DatabaseManagement";
import RestartConfirmationModal from "Frontend/components/general/modals/RestartConfirmationModal";

function SystemManagementLayout() {

    const [confirmRestart, setConfirmRestart] = useState(false);

    return (
        <div className="flex flex-col mt-4">
            <Section title="Dashboard Server Message"/>
            <CheckboxInput key="motd-enabled" name="motd-enabled" label="Show Server Message"/>
            <TextAreaInput key="motd" name="motd" label="Server Message (HTML)"/>

            <Section title="Restart Gameyfin"/>
            <Button onPress={() => setConfirmRestart(true)}>Restart</Button>
            <RestartConfirmationModal isOpen={confirmRestart} onOpenChange={setConfirmRestart}/>
            <BackupManagement/>
            <DatabaseManagement/>
        </div>
    );
}

export const SystemManagement = withConfigPage(SystemManagementLayout, "System");
