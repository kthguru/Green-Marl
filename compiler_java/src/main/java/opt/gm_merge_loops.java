package opt;

import ast.ast_foreach;
import ast.ast_id;
import ast.gm_rwinfo_list;
import ast.gm_rwinfo_map;

import common.gm_resolve_nc;

import frontend.gm_rw_analysis;
import frontend.gm_range_type_t;
import frontend.gm_rwinfo;
import frontend.gm_rwinfo_sets;
import frontend.gm_symtab_entry;

public class gm_merge_loops {

	// ---------------------------------------------------------
	// gm_merge_foreach_if_possible(P, Q)
	// 1. check if mergeable
	// 2. replace driver sym
	// 3. merge two body(see gm_merge_sentlock.cc)
	// ---------------------------------------------------------
	public static boolean is_linear_access_only(gm_rwinfo_list L) {
		assert L != null;
		for (gm_rwinfo rw : L) {
			assert rw != null;
			if (rw.access_range != gm_range_type_t.GM_RANGE_LINEAR)
				return false;
		}
		return true;
	}

	public static boolean intersect_check_for_merge(gm_rwinfo_map S1, gm_rwinfo_map S2, gm_rwinfo_map S1_reduce, boolean check_reduce) {
		for (gm_symtab_entry e : S1.keySet()) {
			if (S2.containsKey(e)) {
				// found entry
				if (!e.getType().is_property()) { // scala
					if (e.getType().is_collection()) {
						if (!e.getType().is_sequential_collection())
							return false;
					}
					return true;
				} else { // check S1, S2 is linearly accessed only.					
					if (!gm_merge_loops.is_linear_access_only(S1.get(e)))
						return true;
					if (!gm_merge_loops.is_linear_access_only(S2.get(e)))
						return true;
					if (check_reduce) 					{ // if e is in the reduce-set,
						if (S1_reduce.containsKey(e))
							return true;
					}
				}
			}
		}

		return false;
	}

	// ----------------------------------------------------
	// example
	// (okay)
	// Foreach(t:G.Nodes) {t.A = x+1;}
	// Foreach(q:G.Nodes) {Q.B = t.A +1 ;} // okay to merge. linear access only
	// for A.
	//
	// (not-okay#1)
	// Foreach(t:G.Nodes) {t.A = x+1;}
	// Foreach(q:G.Nodes) {
	// Foreach(r:q.Nbrs) {
	// q.B += r.A @ q;} // cannot merge t-loop and q-loop because of A is
	// randomly accessed
	// } }
	//
	// (not-okay#2)
	// Foreach(t:G.Nodes){
	// Foreach(r:t.Nbrs)
	// t.A += r.C +1 @ t; // t.A is being reduced
	// }
	// Foreach(q:G.Nodes) {Q.B = t.A +1;} // cannot merge t-loop and q-loop
	// because of A.
	// ----------------------------------------------------
	public static boolean gm_is_mergeable_loops(ast_foreach P, ast_foreach Q) {
		// check same source
		if (P.get_source().getSymInfo() != Q.get_source().getSymInfo())
			return false;

		// check same iteration type
		if (P.get_iter_type() != Q.get_iter_type())
			return false;

		// check same parallel type
		if (P.is_parallel() != Q.is_parallel())
			return false;

		// dependency check for loops.
		// linear access does not make dependency, unless being reduced.
		gm_rwinfo_sets P_SET = gm_rw_analysis.get_rwinfo_sets(P);
		gm_rwinfo_sets Q_SET = gm_rw_analysis.get_rwinfo_sets(Q);

		gm_rwinfo_map P_R = P_SET.read_set;
		gm_rwinfo_map P_W = P_SET.write_set;
		gm_rwinfo_map Q_R = Q_SET.read_set;
		gm_rwinfo_map Q_W = Q_SET.write_set;
		gm_rwinfo_map P_M = P_SET.mutate_set;
		gm_rwinfo_map Q_M = Q_SET.mutate_set;

		gm_rwinfo_sets P_BODY_SET = gm_rw_analysis.get_rwinfo_sets(P.get_body());
		gm_rwinfo_map P_D = P_BODY_SET.reduce_set;

		boolean b;
		// ---------------------------------------------------
		// true dependency check. (P.W -> Q.R)
		// ---------------------------------------------------
		b = gm_merge_loops.intersect_check_for_merge(P_W, Q_R, P_D, true);
		if (b)
			return false;

		// ---------------------------------------------------
		// anti dependency check. (P.R -> Q.W)
		// ---------------------------------------------------
		b = gm_merge_loops.intersect_check_for_merge(P_R, Q_W, P_D, false);
		if (b)
			return false;

		// ---------------------------------------------------
		// anti dependency check. (P.W -> Q.W)
		// ---------------------------------------------------
		b = gm_merge_loops.intersect_check_for_merge(P_W, Q_W, P_D, true);
		if (b)
			return false;

		// ---------------------------------------------------
		// mutate dependency checks.
		// ---------------------------------------------------
		// 1. write mutate check (P.M -> Q.W) || (P.W -> Q.M)
		b = gm_merge_loops.intersect_check_for_merge(P_M, Q_W, P_D, false);
		if (b)
			return false;
		b = gm_merge_loops.intersect_check_for_merge(P_W, Q_M, P_D, false);
		if (b)
			return false;

		// 2. read mutate check (P.M -> Q.R) || (P.R -> Q.M)
		b = gm_merge_loops.intersect_check_for_merge(P_M, Q_R, P_D, false);
		if (b)
			return false;
		b = gm_merge_loops.intersect_check_for_merge(P_R, Q_M, P_D, false);
		if (b)
			return false;

		// 3. mutate mutate check (P.M -> Q.M)
		b = gm_merge_loops.intersect_check_for_merge(P_M, Q_M, P_D, false);
		if (b)
			return false;

		return true;
	}

	public static void replace_iterator_sym(ast_foreach P, ast_foreach Q) {
		// Q's iterator -> P's iterator
		ast_id iter_p = P.get_iterator();
		ast_id iter_q = Q.get_iterator();
		gm_symtab_entry e_p = iter_p.getSymInfo();
		gm_symtab_entry e_q = iter_q.getSymInfo();

		// filter should be NULL. but I'm a paranoid
		assert Q.get_filter() == null;
		assert P.get_filter() == null;

		gm_resolve_nc.gm_replace_symbol_entry(e_q, e_p, Q.get_body());

	}
}