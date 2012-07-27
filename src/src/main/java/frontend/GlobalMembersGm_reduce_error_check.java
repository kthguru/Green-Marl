package frontend;

import static common.GM_ERRORS_AND_WARNINGS.GM_ERROR_DOUBLE_BOUND_ITOR;
import static common.GM_ERRORS_AND_WARNINGS.GM_ERROR_DOUBLE_BOUND_OP;
import inc.GM_REDUCE_T;

import java.util.LinkedList;

import tangible.RefObject;
import ast.ast_id;
import ast.gm_rwinfo_list;
import ast.gm_rwinfo_map;

import common.GlobalMembersGm_error;

public class GlobalMembersGm_reduce_error_check {
	public static boolean is_conflict(LinkedList<bound_info_t> L, gm_symtab_entry t, gm_symtab_entry b, GM_REDUCE_T r_type, RefObject<Boolean> is_bound_error,
			RefObject<Boolean> is_type_error) {
		is_type_error.argvalue = false;
		is_bound_error.argvalue = false;
		for (bound_info_t db : L) {
			if (db.target == t) {
				if (db.bound != b) {
					is_bound_error.argvalue = true;
					return true;
				} else if (db.reduce_type != r_type) {
					is_type_error.argvalue = true;
					return true;
				}
			}
		}
		return false;
	}

	public static void add_bound(LinkedList<bound_info_t> L, gm_symtab_entry t, gm_symtab_entry b, GM_REDUCE_T r_type) {
		bound_info_t T = new bound_info_t();
		T.target = t;
		T.bound = b;
		T.reduce_type = r_type;
		L.addLast(T);
	}

	public static void remove_bound(LinkedList<bound_info_t> L, gm_symtab_entry t, gm_symtab_entry b, GM_REDUCE_T r_type) {
		for (bound_info_t db : L) {
			if ((db.target == t) && (db.reduce_type == r_type) && (db.bound == b)) {
				L.remove(db);
				return;
			}
		}
	}

	// returns is_okay
	public static boolean check_add_and_report_conflicts(LinkedList<bound_info_t> L, gm_rwinfo_map B) {
		for (gm_symtab_entry e : B.keySet()) {
			gm_rwinfo_list l = B.get(e);
			for (gm_rwinfo jj : l) {
				boolean is_bound_error = false;
				boolean is_type_error = false;
				assert jj.bound_symbol != null;
				assert jj.reduce_op != GM_REDUCE_T.GMREDUCE_NULL;
				RefObject<Boolean> tempRef_is_bound_error = new RefObject<Boolean>(is_bound_error);
				RefObject<Boolean> tempRef_is_type_error = new RefObject<Boolean>(is_type_error);
				boolean tempVar = GlobalMembersGm_reduce_error_check.is_conflict(L, e, jj.bound_symbol, jj.reduce_op, tempRef_is_bound_error,
						tempRef_is_type_error);
				is_bound_error = tempRef_is_bound_error.argvalue;
				is_type_error = tempRef_is_type_error.argvalue;
				if (tempVar) {
					ast_id loc = jj.location;
					if (is_bound_error) {
						GlobalMembersGm_error.gm_type_error(GM_ERROR_DOUBLE_BOUND_ITOR, loc.get_line(), loc.get_col(), jj.bound_symbol.getId().get_orgname());
						return false;
					}
					if (is_type_error) {
						GlobalMembersGm_error.gm_type_error(GM_ERROR_DOUBLE_BOUND_OP, loc.get_line(), loc.get_col(), jj.reduce_op.get_reduce_string());
						return false;
					}
				} else {
					GlobalMembersGm_reduce_error_check.add_bound(L, e, jj.bound_symbol, jj.reduce_op);
				}
			}
		}
		return true;
	}

	public static void remove_all(LinkedList<bound_info_t> L, gm_rwinfo_map B) {
		for (gm_symtab_entry e : B.keySet()) {
			gm_rwinfo_list l = B.get(e);
			for (gm_rwinfo jj : l) {
				GlobalMembersGm_reduce_error_check.remove_bound(L, e, jj.bound_symbol, jj.reduce_op);
			}
		}
	}
}