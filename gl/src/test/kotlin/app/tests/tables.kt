package app.tests

// TODO resync
//#ifdef IMGUI_HAS_TABLE
//
//static ImGuiTableColumn* HelperTableFindColumnByName(ImGuiTable* table, const char* name)
//{
//    for (int i = 0; i < table->Columns.size(); i++)
//    if (strcmp(ImGui::TableGetColumnName(table, i), name) == 0)
//        return &table->Columns[i];
//    return NULL;
//}
//
//static void HelperTableSubmitCells(int count_w, int count_h)
//{
//    for (int line = 0; line < count_h; line++)
//    {
//        ImGui::TableNextRow();
//        for (int column = 0; column < count_w; column++)
//        {
//            if (!ImGui::TableSetColumnIndex(column))
//                continue;
//            Str16f label("%d,%d", line, column);
//            //ImGui::TextUnformatted(label.c_str());
//            ImGui::Button(label.c_str(), ImVec2(-FLT_MIN, 0.0f));
//        }
//    }
//}
//// columns_desc = "WWW", "FFW", "FAA" etc.
//static void HelperTableWithResizingPolicies(const char* table_id, ImGuiTableFlags table_flags, const char* columns_desc)
//{
//    table_flags |= ImGuiTableFlags_Resizable | ImGuiTableFlags_Hideable | ImGuiTableFlags_Reorderable | ImGuiTableFlags_Borders | ImGuiTableFlags_NoSavedSettings;
//    int columns_count = (int)strlen(columns_desc);
//    IM_ASSERT(columns_count < 26); // Because we are using alphabetical letters for names
//    if (!ImGui::BeginTable(table_id, columns_count, table_flags))
//        return;
//    for (int column = 0; column < columns_count; column++)
//    {
//        const char policy = columns_desc[column];
//        ImGuiTableColumnFlags column_flags = ImGuiTableColumnFlags_None;
//        if      (policy >= 'a' && policy <= 'z') { column_flags |= ImGuiTableColumnFlags_DefaultHide; }
//        if      (policy == 'f' || policy == 'F') { column_flags |= ImGuiTableColumnFlags_WidthFixed; }
//        else if (policy == 'w' || policy == 'W') { column_flags |= ImGuiTableColumnFlags_WidthStretch; }
//        else if (policy == 'a' || policy == 'A') { column_flags |= ImGuiTableColumnFlags_WidthAlwaysAutoResize; }
//        else IM_ASSERT(0);
//        ImGui::TableSetupColumn(Str16f("%c%d", policy, column + 1).c_str(), column_flags);
//    }
//    ImFont* font = FindFontByName("Roboto-Medium.ttf, 16px");
//    if (!font)
//        IM_CHECK_NO_RET(font != NULL);
//    ImGui::PushFont(font);
//    ImGui::TableAutoHeaders();
//    ImGui::PopFont();
//    for (int row = 0; row < 2; row++)
//    {
//        ImGui::TableNextRow();
//        for (int column = 0; column < columns_count; column++)
//        {
//            ImGui::TableSetColumnIndex(column);
//            const char* column_desc = "Unknown";
//            const char policy = columns_desc[column];
//            if (policy == 'F') { column_desc = "Fixed"; }
//            if (policy == 'W') { column_desc = "Stretch"; }
//            if (policy == 'A') { column_desc = "Auto"; }
//            ImGui::Text("%s %d,%d", column_desc, row, column);
//        }
//    }
//    ImGui::EndTable();
//}
//#endif // #ifdef IMGUI_HAS_TABLE
//void RegisterTests_Table(ImGuiTestEngine* e)
//{
//    #ifdef IMGUI_HAS_TABLE
//        ImGuiTest* t = NULL;
//    t = REGISTER_TEST("table", "table_1");
//    t->GuiFunc = [](ImGuiTestContext* ctx)
//    {
//        ImGui::Begin("Test window 1", NULL, ImGuiWindowFlags_NoSavedSettings);
//        if (ImGui::BeginTable("##table0", 4))
//        {
//            ImGui::TableSetupColumn("One", ImGuiTableColumnFlags_WidthFixed, 100.0f, 0);
//            ImGui::TableSetupColumn("Two");
//            ImGui::TableSetupColumn("Three");
//            ImGui::TableSetupColumn("Four");
//            HelperTableSubmitCells(4, 5);
//            ImGuiTable* table = ctx->UiContext->CurrentTable;
//            IM_CHECK_EQ(table->Columns[0].WidthRequest, 100.0f);
//            ImGui::EndTable();
//        }
//        ImGui::End();
//    };
//    // ## Table: measure draw calls count
//    // FIXME-TESTS: Resize window width to e.g. ideal size first, then resize down
//    // Important: HelperTableSubmitCells uses Button() with -1 width which will CPU clip text, so we don't have interference from the contents here.
//    t = REGISTER_TEST("table", "table_2_draw_calls");
//    t->GuiFunc = [](ImGuiTestContext* ctx)
//    {
//        ImGui::Begin("Test window 1", NULL, ImGuiWindowFlags_NoSavedSettings);
//        ImDrawList* draw_list = ImGui::GetWindowDrawList();
//        ImGui::Text("Text before");
//        {
//            int cmd_size_before = draw_list->CmdBuffer.Size;
//            if (ImGui::BeginTable("##table1", 4, ImGuiTableFlags_NoClip | ImGuiTableFlags_Borders, ImVec2(400, 0)))
//            {
//                HelperTableSubmitCells(4, 5);
//                ImGui::EndTable();
//            }
//            ImGui::Text("Some text");
//            int cmd_size_after = draw_list->CmdBuffer.Size;
//            IM_CHECK_EQ(cmd_size_before, cmd_size_after);
//        }
//        {
//            int cmd_size_before = draw_list->CmdBuffer.Size;
//            if (ImGui::BeginTable("##table2", 4, ImGuiTableFlags_Borders, ImVec2(400, 0)))
//            {
//                HelperTableSubmitCells(4, 4);
//                ImGui::EndTable();
//            }
//            ImGui::Text("Some text");
//            int cmd_size_after = draw_list->CmdBuffer.Size;
//            IM_CHECK_EQ(cmd_size_before, cmd_size_after);
//        }
//        {
//            int cmd_size_before = draw_list->CmdBuffer.Size;
//            if (ImGui::BeginTable("##table3", 4, ImGuiTableFlags_Borders, ImVec2(400, 0)))
//            {
//                ImGui::TableSetupColumn("One");
//                ImGui::TableSetupColumn("TwoTwo");
//                ImGui::TableSetupColumn("ThreeThreeThree");
//                ImGui::TableSetupColumn("FourFourFourFour");
//                ImGui::TableAutoHeaders();
//                HelperTableSubmitCells(4, 4);
//                ImGui::EndTable();
//            }
//            ImGui::Text("Some text");
//            int cmd_size_after = draw_list->CmdBuffer.Size;
//            IM_CHECK_EQ(cmd_size_before, cmd_size_after);
//        }
//        {
//            int cmd_size_before = draw_list->CmdBuffer.Size;
//            if (ImGui::BeginTable("##table4", 3, ImGuiTableFlags_Borders))
//            {
//                ImGui::TableSetupColumn("One");
//                ImGui::TableSetupColumn("TwoTwo");
//                ImGui::TableSetupColumn("ThreeThreeThree");
//                ImGui::TableAutoHeaders();
//                HelperTableSubmitCells(3, 4);
//                ImGui::EndTable();
//            }
//            ImGui::Text("Some text");
//            int cmd_size_after = draw_list->CmdBuffer.Size;
//            IM_CHECK_EQ(cmd_size_before, cmd_size_after);
//        }
//        ImGui::End();
//    };

//t->TestFunc = [](ImGuiTestContext* ctx)
//{
//    // Test with/without clipping
//    ctx->WindowResize("Test window 1", ImVec2(500, 600));
//    ctx->WindowResize("Test window 1", ImVec2(10, 600));
//};
//    // ## Table: measure equal width
//    t = REGISTER_TEST("table", "table_3_width");
//    t->GuiFunc = [](ImGuiTestContext* ctx)
//    {
//        ImGui::SetNextWindowSize(ImVec2(400, 0), ImGuiCond_Appearing);
//        ImGui::Begin("Test window 1", NULL, ImGuiWindowFlags_NoSavedSettings);
//        struct TestCase
//                {
//                    int             ColumnCount;
//                    ImGuiTableFlags Flags;
//                };
//        TestCase test_cases[] =
//                {
//                    { 2, ImGuiTableFlags_None },
//                    { 3, ImGuiTableFlags_None },
//                    { 9, ImGuiTableFlags_None },
//                    { 2, ImGuiTableFlags_BordersOuter },
//                    { 3, ImGuiTableFlags_BordersOuter },
//                    { 9, ImGuiTableFlags_BordersOuter },
//                    { 2, ImGuiTableFlags_BordersV },
//                    { 3, ImGuiTableFlags_BordersV },
//                    { 9, ImGuiTableFlags_BordersV },
//                    { 2, ImGuiTableFlags_Borders },
//                    { 3, ImGuiTableFlags_Borders },
//                    { 9, ImGuiTableFlags_Borders },
//                };
//        ImGui::Text("(width variance should be <= 1.0f)");
//        for (int test_case_n = 0; test_case_n < IM_ARRAYSIZE(test_cases); test_case_n++)
//        {
//            const TestCase& tc = test_cases[test_case_n];
//            ImGui::PushID(test_case_n);
//            ImGui::Spacing();
//            ImGui::Spacing();
//            if (ImGui::BeginTable("##table", tc.ColumnCount, tc.Flags, ImVec2(0, 0)))
//            {
//                ImGui::TableNextRow();
//                float min_w = FLT_MAX;
//                float max_w = -FLT_MAX;
//                for (int n = 0; n < tc.ColumnCount; n++)
//                {
//                    ImGui::TableSetColumnIndex(n);
//                    float w = ImGui::GetContentRegionAvail().x;
//                    min_w = ImMin(w, min_w);
//                    max_w = ImMax(w, max_w);
//                    ImGui::Text("Width %.2f", w);
//                }
//                float w_variance = max_w - min_w;
//                IM_CHECK_LE_NO_RET(w_variance, 1.0f);
//                ImGui::EndTable();
//                if (w_variance > 1.0f)
//                    ImGui::PushStyleColor(ImGuiCol_Text, IM_COL32(255, 100, 100, 255));
//                ImGui::Text("#%02d: Variance %.2f (min %.2f max %.2f)", test_case_n, w_variance, min_w, max_w);
//                if (w_variance > 1.0f)
//                    ImGui::PopStyleColor();
//            }
//            ImGui::PopID();
//        }
//        ImGui::End();
//    };
//    // ## Test behavior of some Table functions without BeginTable
//    t = REGISTER_TEST("table", "table_4_functions_without_table");
//    t->TestFunc = [](ImGuiTestContext* ctx)
//    {
//        ImGui::Begin("Test window 1", NULL, ImGuiWindowFlags_NoSavedSettings);
//        IM_CHECK_EQ(ImGui::TableGetColumnIndex(), 0);
//        IM_CHECK_EQ(ImGui::TableSetColumnIndex(42), false);
//        IM_CHECK_EQ(ImGui::TableGetColumnIsVisible(0), false);
//        IM_CHECK_EQ(ImGui::TableGetColumnIsSorted(0), false);
//        IM_CHECK_EQ(ImGui::TableGetColumnName(), (const char*)NULL);
//        ImGui::End();
//    };
//    // ## Resizing test-bed (not an actual automated test)
//    t = REGISTER_TEST("table", "table_5_resizing_behaviors");
//    t->GuiFunc = [](ImGuiTestContext* ctx)
//    {
//        ImGui::SetNextWindowPos(ctx->GetMainViewportPos() + ImVec2(20, 5), ImGuiCond_Once);
//        ImGui::SetNextWindowSize(ImVec2(400.0f, 0.0f), ImGuiCond_Once);
//        ImGui::Begin("Test window 1", NULL, ImGuiWindowFlags_NoSavedSettings);
//        ImGui::BulletText("OK: Resize from F1| or F2|");    // ok: alter ->WidthRequested of Fixed column. Subsequent columns will be offset.
//        ImGui::BulletText("OK: Resize from F3|");           // ok: alter ->WidthRequested of Fixed column. If active, ScrollX extent can be altered.
//        HelperTableWithResizingPolicies("table1", 0, "FFF");
//        ImGui::Spacing();
//        ImGui::BulletText("OK: Resize from F1| or F2|");    // ok: alter ->WidthRequested of Fixed column. If active, ScrollX extent can be altered, but it doesn't make much sense as the Weighted column will always be minimal size.
//        ImGui::BulletText("OK: Resize from W3| (off)");     // ok: no-op (disabled by Rule A)
//        HelperTableWithResizingPolicies("table2", 0, "FFW");
//        ImGui::Spacing();
//        ImGui::BulletText("KO: Resize from W1| or W2|");    // FIXME: not implemented
//        ImGui::BulletText("OK: Resize from W3| (off)");     // ok: no-op (disabled by Rule A)
//        HelperTableWithResizingPolicies("table3", 0, "WWWw");
//        ImGui::Spacing();
//        // Need F2w + F3w to be stable to avoid moving W1
//        // lock F2L
//        // move F2R
//        // move F3L
//        // lock F3R
//        ImGui::BulletText("OK: Resize from W1| (fwd)");     // ok: forward to resizing |F2. F3 will not move.
//        ImGui::BulletText("KO: Resize from F2| or F3|");    // FIXME should resize F2, F3 and not have effect on W1 (Weighted columns are _before_ the Fixed column).
//        ImGui::BulletText("OK: Resize from F4| (off)");     // ok: no-op (disabled by Rule A)
//        HelperTableWithResizingPolicies("table4", 0, "WFFF");
//        ImGui::Spacing();
//        ImGui::BulletText("OK: Resize from W1| (fwd)");     // ok: forward to resizing |F2
//        ImGui::BulletText("OK: Resize from F2| (off)");     // ok: no-op (disabled by Rule A)
//        HelperTableWithResizingPolicies("table5", 0, "WF");
//        ImGui::Spacing();
//        ImGui::BulletText("KO: Resize from W1|");           // FIXME
//        ImGui::BulletText("KO: Resize from W2|");           // FIXME
//        HelperTableWithResizingPolicies("table6", 0, "WWF");
//        ImGui::Spacing();
//        ImGui::BulletText("KO: Resize from W1|");           // FIXME
//        ImGui::BulletText("KO: Resize from F2|");           // FIXME
//        HelperTableWithResizingPolicies("table7", 0, "WFW");
//        ImGui::Spacing();
//        ImGui::BulletText("OK: Resize from W2| (fwd)");     // ok: forward
//        HelperTableWithResizingPolicies("table8", 0, "FWF");
//        ImGui::Spacing();
//        ImGui::BulletText("OK: Resize from ");
//        HelperTableWithResizingPolicies("table9", 0, "WWFWW");
//        ImGui::Spacing();
//        ImGui::End();
//    };
//    // ## Test Visible flag
//    t = REGISTER_TEST("table", "table_6_clip");
//    t->Flags |= ImGuiTestFlags_NoAutoFinish;
//    t->GuiFunc = [](ImGuiTestContext* ctx)
//    {
//        ImGui::Begin("Test window 1", NULL, ImGuiWindowFlags_NoSavedSettings);
//        if (ImGui::BeginTable("table1", 4, ImGuiTableFlags_ScrollX | ImGuiTableFlags_Borders, ImVec2(200, 200)))
//        {
//            ImGui::TableSetupColumn("One", 0, 80);
//            ImGui::TableSetupColumn("Two", 0, 80);
//            ImGui::TableSetupColumn("Three", 0, 80);
//            ImGui::TableSetupColumn("Four", 0, 80);
//            for (int row = 0; row < 2; row++)
//            {
//                ImGui::TableNextRow();
//                bool visible_0 = ImGui::TableSetColumnIndex(0);
//                ImGui::Text(visible_0 ? "1" : "0");
//                bool visible_1 = ImGui::TableSetColumnIndex(1);
//                ImGui::Text(visible_1 ? "1" : "0");
//                bool visible_2 = ImGui::TableSetColumnIndex(2);
//                ImGui::Text(visible_2 ? "1" : "0");
//                bool visible_3 = ImGui::TableSetColumnIndex(3);
//                ImGui::Text(visible_3 ? "1" : "0");
//                if (ctx->FrameCount > 1)
//                {
//                    IM_CHECK_EQ(visible_0, true);
//                    IM_CHECK_EQ(visible_1, true);
//                    IM_CHECK_EQ(visible_2, true); // Half Visible
//                    IM_CHECK_EQ(visible_3, false);
//                    ctx->Finish();
//                }
//            }
//            ImGui::EndTable();
//        }
//        ImGui::End();
//    };
//    // ## Test that BeginTable/EndTable with no contents doesn't fail
//    t = REGISTER_TEST("table", "table_7_empty");
//    t->GuiFunc = [](ImGuiTestContext* ctx)
//    {
//        ImGui::Begin("Test window 1", NULL, ImGuiWindowFlags_NoSavedSettings);
//        ImGui::BeginTable("Table", 3);
//        ImGui::EndTable();
//        ImGui::End();
//    };
//    // ## Test default sort order assignment
//    t = REGISTER_TEST("table", "table_8_default_sort_order");
//    t->GuiFunc = [](ImGuiTestContext* ctx)
//    {
//        ImGui::Begin("Test window 1", NULL, ImGuiWindowFlags_NoSavedSettings);
//        ImGui::BeginTable("Table", 4, ImGuiTableFlags_MultiSortable);
//        ImGui::TableSetupColumn("0", ImGuiTableColumnFlags_None);
//        ImGui::TableSetupColumn("1", ImGuiTableColumnFlags_DefaultSort);
//        ImGui::TableSetupColumn("1", ImGuiTableColumnFlags_DefaultSort | ImGuiTableColumnFlags_PreferSortAscending);
//        ImGui::TableSetupColumn("2", ImGuiTableColumnFlags_DefaultSort | ImGuiTableColumnFlags_PreferSortDescending);
//        const ImGuiTableSortSpecs* sort_specs = ImGui::TableGetSortSpecs();
//        ImGui::TableAutoHeaders();
//        IM_CHECK_EQ(sort_specs->SpecsCount, 3);
//        IM_CHECK_EQ(sort_specs->Specs[0].ColumnIndex, 1);
//        IM_CHECK_EQ(sort_specs->Specs[1].ColumnIndex, 2);
//        IM_CHECK_EQ(sort_specs->Specs[2].ColumnIndex, 3);
//        IM_CHECK_EQ(sort_specs->Specs[0].SortDirection, ImGuiSortDirection_Ascending);
//        IM_CHECK_EQ(sort_specs->Specs[1].SortDirection, ImGuiSortDirection_Ascending);
//        IM_CHECK_EQ(sort_specs->Specs[2].SortDirection, ImGuiSortDirection_Descending);
//        //ImGuiTable* table = ctx->UiContext->CurrentTable;
//        //IM_CHECK_EQ(table->SortSpecsCount, 3);
//        ImGui::EndTable();
//        ImGui::End();
//    };
//
//    // ## Test using the maximum of 64 columns (#3058)
//    t = REGISTER_TEST("table", "table_9_max_columns");
//    t->GuiFunc = [](ImGuiTestContext* ctx)
//    {
//        ImGui::Begin("Test window 1", NULL, ImGuiWindowFlags_NoSavedSettings);
//        //ImDrawList* cmd = ImGui::GetWindowDrawList();
//        ImGui::BeginTable("Table", 64);
//        for (int n = 0; n < 64; n++)
//        ImGui::TableSetupColumn("Header");
//        ImGui::TableAutoHeaders();
//        for (int i = 0; i < 10; i++)
//        {
//            ImGui::TableNextRow();
//            for (int n = 0; n < 64; n++)
//            {
//                ImGui::Text("Data");
//                ImGui::TableNextCell();
//            }
//        }
//        ImGui::EndTable();
//        ImGui::End();
//    };
//
