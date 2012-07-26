package backend_cpp;

import frontend.GlobalMembersGm_rw_analysis;
import inc.GlobalMembersGm_backend_cpp;
import inc.gm_compile_step;

import java.util.LinkedList;

import ast.ast_extra_info_list;
import ast.ast_procdef;
import ast.ast_sent;

public class gm_cpp_gen_check_bfs extends gm_compile_step {
	private gm_cpp_gen_check_bfs() {
		set_description("Check BFS routines");
	}

	public void process(ast_procdef d) {
		// re-do rw analysis
		GlobalMembersGm_rw_analysis.gm_redo_rw_analysis(d.get_body());

		check_bfs_main_t T = new check_bfs_main_t(d);
		d.traverse_both(T);

		d.add_info_bool(GlobalMembersGm_backend_cpp.CPPBE_INFO_HAS_BFS, T.has_bfs);
		if (T.has_bfs) {
			// C++ TO JAVA CONVERTER WARNING: The following line was determined
			// to be a copy constructor call - this should be verified and a
			// copy constructor should be created if it does not yet exist:
			// ORIGINAL LINE: LinkedList<ast_sent*>&L = T.bfs_lists;
			LinkedList<ast_sent> L = new LinkedList<ast_sent>(T.bfs_lists);
			ast_extra_info_list BL = new ast_extra_info_list();
			for (ast_sent s : L) {
				BL.get_list().addLast(s);
			}
			d.add_info(GlobalMembersGm_backend_cpp.CPPBE_INFO_BFS_LIST, BL);
		}
	}

	@Override
	public gm_compile_step get_instance() {
		return new gm_cpp_gen_check_bfs();
	}

	public static gm_compile_step get_factory() {
		return new gm_cpp_gen_check_bfs();
	}
}