package engine.context

//#ifdef IMGUI_HAS_TABLE
//
//void    ImGuiTestContext::TableDiscard(ImGuiTable* table)
//{
//    ImGuiContext& g = *GImGui;
//    IM_ASSERT(g.CurrentTable == NULL);
//    if (ImGuiTableSettings* settings = ImGui::TableGetBoundSettings(table))
//        settings->ID = 0;
//    g.Tables.Remove(table->ID, table);
//}
//
//#endif // #ifdef IMGUI_HAS_TABLE