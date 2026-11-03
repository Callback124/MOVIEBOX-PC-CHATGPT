"""
Test and Verification Script for all 6 MovieBox Server Extensions
"""

import sys
import os

# Add parent directory to path so relative imports work
curr_dir = os.path.dirname(os.path.abspath(__file__))
parent_dir = os.path.dirname(curr_dir)
if parent_dir not in sys.path:
    sys.path.insert(0, parent_dir)

from extensions.extension_manager import ExtensionManager


def run_tests():
    print("=" * 65)
    print("TESTING ALL 6 STREAMING SERVER EXTENSIONS")
    print("=" * 65)

    servers = [
        ("server_1", "MovieBox", "Spider"),
        ("server_2", "VSKit", "CEO"),
        ("server_3", "Lookr", "Action"),
        ("server_4", "Story TV", "Drama"),
        ("server_5", "Free Reels", "Love"),
        ("server_6", "JioSaavn", "Arijit")
    ]

    for s_id, s_name, test_kw in servers:
        print(f"\n--- Testing Server: {s_name} ({s_id}) ---")
        ext = ExtensionManager.get(s_id)
        if not ext:
            print(f"❌ Failed to load extension for {s_id}")
            continue

        # 1. Test Home Data (Row Format)
        try:
            home = ext.get_home_data()
            banners = home.get("hero_banners", [])
            rows = home.get("rows", [])
            print(f"  [+] get_home_data(): SUCCESS ({len(banners)} hero banners, {len(rows)} rows)")
            for r in rows[:2]:
                print(f"      - Row: '{r.get('title')}' with {len(r.get('items', []))} items")
        except Exception as e:
            print(f"  [-] get_home_data(): FAILED -> {e}")

        # 2. Test Search Suggestions
        try:
            suggs = ext.get_search_suggestions(test_kw)
            print(f"  [+] get_search_suggestions('{test_kw}'): SUCCESS -> {suggs[:3]}")
        except Exception as e:
            print(f"  [-] get_search_suggestions(): FAILED -> {e}")

        # 3. Test Search
        first_item = None
        try:
            s_res = ext.search(test_kw, page=1, per_page=5)
            items = s_res.get("items", [])
            print(f"  [+] search('{test_kw}'): SUCCESS ({len(items)} items, has_more={s_res.get('has_more')})")
            if items:
                first_item = items[0]
                print(f"      Sample: '{first_item.get('title')}' (id={first_item.get('id')})")
        except Exception as e:
            print(f"  [-] search(): FAILED -> {e}")

        # 4. Test Detail & Player Stream
        if first_item:
            sid = first_item.get("id") or first_item.get("subjectId")
            path = first_item.get("detailPath", "")
            try:
                det = ext.get_detail(sid, path)
                print(f"  [+] get_detail(): SUCCESS (title='{det.get('title') if det else 'N/A'}')")
            except Exception as e:
                print(f"  [-] get_detail(): FAILED -> {e}")

            try:
                stream = ext.get_player_stream(sid, path, season=0, episode=0)
                print(f"  [+] get_player_stream(): SUCCESS (has_resource={stream.get('has_resource')}, streams={len(stream.get('streams', []))})")
                if stream.get("streams"):
                    print(f"      Stream url sample: {stream['streams'][0].get('url', '')[:60]}...")
            except Exception as e:
                print(f"  [-] get_player_stream(): FAILED -> {e}")

    print("\n" + "=" * 65)
    print("ALL 6 SERVER EXTENSIONS TEST SUITE COMPLETED!")
    print("=" * 65)


if __name__ == "__main__":
    run_tests()
