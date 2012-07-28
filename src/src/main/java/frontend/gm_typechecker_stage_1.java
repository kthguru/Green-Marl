package frontend;

import static common.GM_ERRORS_AND_WARNINGS.GM_ERROR_INVALID_ITERATOR_FOR_RARROW;
import static common.GM_ERRORS_AND_WARNINGS.GM_ERROR_INVALID_OUTPUT_TYPE;
import static common.GM_ERRORS_AND_WARNINGS.GM_ERROR_NEED_BFS_ITERATION;
import static common.GM_ERRORS_AND_WARNINGS.GM_ERROR_NEED_ITERATOR;
import static common.GM_ERRORS_AND_WARNINGS.GM_ERROR_NONGRAPH_FIELD;
import static common.GM_ERRORS_AND_WARNINGS.GM_ERROR_NONNODE_TARGET;
import static common.GM_ERRORS_AND_WARNINGS.GM_ERROR_TARGET_MISMATCH;
import static common.GM_ERRORS_AND_WARNINGS.GM_ERROR_WRONG_PROPERTY;
import static frontend.GlobalMembersGm_typecheck.GM_READ_AVAILABLE;
import static frontend.GlobalMembersGm_typecheck.GM_READ_NOT_AVAILABLE;
import static frontend.GlobalMembersGm_typecheck.GM_WRITE_AVAILABLE;
import static frontend.GlobalMembersGm_typecheck.GM_WRITE_NOT_AVAILABLE;
import inc.GMTYPE_T;

import java.util.LinkedList;

import ast.AST_NODE_TYPE;
import ast.ast_argdecl;
import ast.ast_assign;
import ast.ast_bfs;
import ast.ast_expr;
import ast.ast_expr_builtin;
import ast.ast_expr_builtin_field;
import ast.ast_expr_foreign;
import ast.ast_expr_reduce;
import ast.ast_field;
import ast.ast_foreach;
import ast.ast_foreign;
import ast.ast_id;
import ast.ast_idlist;
import ast.ast_node;
import ast.ast_procdef;
import ast.ast_sent;
import ast.ast_typedecl;
import ast.ast_vardecl;

import common.GlobalMembersGm_error;
import common.GlobalMembersGm_main;
import common.gm_apply;

//----------------------------------------------------------------
// Type-check  Step 1: 
//     (1) create a hierarchy of symbol tables
//     (2) Add symbols into symbol table
//     (3) create a connection ID <-> symbol
//     (4) Check rules related to ID
//           - procedure:
//                 * return should be primitive or node/edge
//                 * output args should be primitive or node/edge
//           - declarations: target graph should be well defined.
//             (property, node, edge, collection)
//
//           - property should be primitive or node/edge
//                
//           - iterators: 
//                  * target graph(set) should be well defined
//                  * node iterator should begin from node, edge iterator should begin from edge
//                  * up/down should be start from bfs iterator
//           - bfs
//                  * src should be a graph
//                  * root should be a node and belong to the same src
//           - property access:
//                   * target graph should match
//           
//----------------------------------------------------------------
public class gm_typechecker_stage_1 extends gm_apply {

	// symbol tables
	private LinkedList<gm_symtab> var_syms = new LinkedList<gm_symtab>();
	private LinkedList<gm_symtab> field_syms = new LinkedList<gm_symtab>();
	private LinkedList<gm_symtab> proc_syms = new LinkedList<gm_symtab>();

	private gm_symtab curr_sym = null;
	private gm_symtab curr_field = null;
	private gm_symtab curr_proc = null;

	private boolean _is_okay = true;

	public gm_typechecker_stage_1() {
		set_for_expr(true);
		set_for_sent(true);
		set_for_proc(true);
	}

	// --------------------------------------------------------
	// Add arguments to the current symbol table
	@Override
	public boolean apply(ast_procdef p) {
		boolean is_okay = true;
		// add arguments to the current symbol table
		LinkedList<ast_argdecl> in_args = p.get_in_args();
		for (ast_argdecl a : in_args) {
			ast_typedecl type = a.get_type();
			boolean b = GlobalMembersGm_new_typecheck_step1.gm_check_type_is_well_defined(type, curr_sym);
			is_okay = b && is_okay;
			if (b) {
				ast_idlist idlist = a.get_idlist();
				gm_symtab S = type.is_property() ? curr_field : curr_sym;
				for (int i = 0; i < idlist.get_length(); i++) {
					ast_id id = idlist.get_item(i);
					is_okay = GlobalMembersGm_new_typecheck_step1.gm_declare_symbol(S, id, type, GM_READ_AVAILABLE, GM_WRITE_NOT_AVAILABLE) && is_okay;
					if (is_okay) {
						id.getSymInfo().setArgument(true);
					}
				}
			}
		}

		LinkedList<ast_argdecl> out_args = p.get_out_args();
		for (ast_argdecl a : out_args) {
			ast_typedecl type = a.get_type();
			boolean b = GlobalMembersGm_new_typecheck_step1.gm_check_type_is_well_defined(type, curr_sym);
			is_okay = b && is_okay;
			if (b) {
				// ast_idlist idlist = a.get_idlist();
				// only primitives or nodes or edges can be an output
				if (!type.is_primitive() && !type.is_nodeedge()) {
					GlobalMembersGm_error.gm_type_error(GM_ERROR_INVALID_OUTPUT_TYPE, type.get_line(), type.get_col());
					is_okay = false;
				} else {
					ast_idlist idlist = a.get_idlist();
					for (int i = 0; i < idlist.get_length(); i++) {
						ast_id id = idlist.get_item(i);
						is_okay = GlobalMembersGm_new_typecheck_step1.gm_declare_symbol(curr_sym, id, type, GM_READ_NOT_AVAILABLE, GM_WRITE_AVAILABLE)
								&& is_okay;
						if (is_okay) {
							id.getSymInfo().setArgument(true);
						}
					}
				}
			}
		}

		// ---------------------------------------
		// crete return type
		// ---------------------------------------
		ast_typedecl ret;
		ret = p.get_return_type();
		if (ret == null) {
			ret = ast_typedecl.new_void();
			p.set_return_type(ret);
		}
		is_okay = GlobalMembersGm_new_typecheck_step1.gm_check_type_is_well_defined(ret, curr_sym) && is_okay;
		if (!ret.is_void() && !ret.is_primitive() && !ret.is_nodeedge()) {
			GlobalMembersGm_error.gm_type_error(GM_ERROR_INVALID_OUTPUT_TYPE, ret.get_line(), ret.get_col());
			is_okay = false;
		}

		set_okay(is_okay);
		return is_okay;

	}

	@Override
	public boolean apply(ast_expr p) {
		boolean is_okay = true;
		switch (p.get_opclass()) {
		case GMEXPR_ID: {
			is_okay = find_symbol_id(p.get_id());
			break;
		}
		case GMEXPR_FIELD: {
			is_okay = find_symbol_field(p.get_field());
			break;
		}
		case GMEXPR_REDUCE: {
			ast_expr_reduce r = (ast_expr_reduce) p;
			GMTYPE_T iter_type = r.get_iter_type();
			is_okay = gm_symbol_check_iter_header(r.get_iterator(), r.get_source(), iter_type, r.get_source2());
			if (iter_type.is_unknown_collection_iter_type()) // resolve unknown
																// iterator
				r.set_iter_type(r.get_iterator().getTypeSummary());
			break;
		}
		case GMEXPR_BUILTIN: {
			ast_expr_builtin b = (ast_expr_builtin) p;
			ast_id i = b.get_driver();
			if (i != null)
				is_okay = find_symbol_id(i);
			if (is_okay) {
				@SuppressWarnings("unused")
				GMTYPE_T source_type = (i == null) ? GMTYPE_T.GMTYPE_VOID : i.getTypeSummary();
			}
			break;
		}
		case GMEXPR_BUILTIN_FIELD: {
			ast_expr_builtin_field builtinField = (ast_expr_builtin_field) p;
			ast_field field = builtinField.get_field_driver();
			is_okay = find_symbol_field(field);
			is_okay &= find_symbol_id(field.get_first());
			break;
		}
		case GMEXPR_FOREIGN: {
			ast_expr_foreign f = (ast_expr_foreign) p;
			LinkedList<ast_node> L = f.get_parsed_nodes();
			for (ast_node n : L) {
				if (n == null)
					continue;
				if (n.get_nodetype() == AST_NODE_TYPE.AST_FIELD) {
					is_okay = find_symbol_field((ast_field) n) && is_okay;
				} else {
					boolean b = find_symbol_id((ast_id) n, false);
					if (!b) {
						b = find_symbol_field_id((ast_id) n);
					}
					is_okay = b && is_okay;
				}
			}
			break;
		}
		default:
			break;
		}

		set_okay(is_okay);

		return is_okay;
	}

	@Override
	public boolean apply(ast_sent s) {
		boolean is_okay = true;
		switch (s.get_nodetype()) {
		// Add variable declaration
		case AST_VARDECL: {
			ast_vardecl v = (ast_vardecl) s;
			ast_typedecl type = v.get_type();
			is_okay = GlobalMembersGm_new_typecheck_step1.gm_check_type_is_well_defined(type, curr_sym);

			// add current declaration
			if (is_okay) {
				ast_idlist idlist = v.get_idlist();
				gm_symtab S = type.is_property() ? curr_field : curr_sym;
				for (int i = 0; i < idlist.get_length(); i++) {
					ast_id id = idlist.get_item(i);
					is_okay = GlobalMembersGm_new_typecheck_step1.gm_declare_symbol(S, id, type, GM_READ_AVAILABLE, GM_WRITE_AVAILABLE) && is_okay;
				}
			}

			v.set_tc_finished(true); // why?
			break;
		}
		// check lhs and bound symbol
		case AST_ASSIGN: {
			ast_assign a = (ast_assign) s;
			// lhs
			if (a.is_target_scalar()) {
				ast_id id = a.get_lhs_scala();
				is_okay = find_symbol_id(id);
			} else {
				ast_field f = a.get_lhs_field();
				is_okay = find_symbol_field(f);
			}

			if (a.is_argminmax_assign()) {
				LinkedList<ast_node> L = a.get_lhs_list();
				for (ast_node n : L) {
					if (n.get_nodetype() == AST_NODE_TYPE.AST_ID) {
						ast_id id = (ast_id) n;
						is_okay = find_symbol_id(id) && is_okay;
					} else {
						ast_field f = (ast_field) n;
						is_okay = find_symbol_field(f) && is_okay;
					}
				}
			}

			// bound symbol
			ast_id bound = a.get_bound();
			if (bound != null) {
				is_okay = find_symbol_id(bound);
				if (is_okay) {
					// bound symbol must be iterator
					if (!bound.getTypeInfo().is_node_edge_iterator() && !bound.getTypeInfo().is_collection_iterator()) {
						GlobalMembersGm_error.gm_type_error(GM_ERROR_NEED_ITERATOR, bound);
						is_okay = false;
					}
				}
			}

			break;
		}
		// check bound symbol
		case AST_FOREACH: {
			ast_foreach fe = (ast_foreach) s;
			GMTYPE_T iter_type = adjust_iter_type(fe);
			is_okay = gm_symbol_check_iter_header(fe.get_iterator(), fe.get_source(), iter_type, fe.get_source2());
			if (!is_okay)
				break;
			if (iter_type.is_unknown_collection_iter_type()) // resolve unknown
																// iterator
			{
				fe.set_iter_type(fe.get_iterator().getTypeSummary());
			}
			break;
		}
		case AST_BFS: {
			ast_bfs bfs = (ast_bfs) s;
			is_okay = gm_symbol_check_bfs_header(bfs.get_iterator(), bfs.get_source(), bfs.get_root(), bfs.get_iter_type());

			// ---------------------------------------------
			// create 2nd iteator
			// ---------------------------------------------
			String tname = GlobalMembersGm_main.FE.voca_temp_name("nx");
			ast_id iter2 = ast_id.new_id(tname, bfs.get_iterator().get_line(), bfs.get_iterator().get_col());
			ast_typedecl type = ast_typedecl.new_nbr_iterator(bfs.get_iterator().copy(true), bfs.get_iter_type2());
			is_okay = GlobalMembersGm_new_typecheck_step1.gm_declare_symbol(curr_sym, iter2, type, GM_READ_AVAILABLE, GM_WRITE_NOT_AVAILABLE) && is_okay;
			if (type != null)
				type.dispose();
			tname = null;
			bfs.set_iterator2(iter2);
			break;
		}
		case AST_FOREIGN: {
			ast_foreign f = (ast_foreign) s;

			// -----------------------------------
			// examine mutation list
			// -----------------------------------
			LinkedList<ast_node> L = f.get_modified();
			for (ast_node node : L) {
				if (node.get_nodetype() == AST_NODE_TYPE.AST_ID) {
					ast_id id = (ast_id) node;
					boolean b = find_symbol_id(id, false);
					if (!b) {
						b = find_symbol_field_id(id);
					}
					is_okay = b && is_okay;
				} else if (node.get_nodetype() == AST_NODE_TYPE.AST_FIELD) {
					ast_field ff = (ast_field) node;
					is_okay = find_symbol_field(ff) && is_okay;
				} else {
					assert false;
				}
			}
			break;
		}
		// expressions will be considiered when apply(ast_expr*) is invoked
		case AST_SENTBLOCK:
		case AST_CALL:
		case AST_WHILE:
		case AST_IF:
		case AST_NOP:
		case AST_RETURN:
			break;
		default:
			System.out.println("type = " + s.get_nodetype().get_nodetype_string());
			assert false;
			break;
		}
		set_okay(is_okay);

		return is_okay;
	}

	@Override
	public void begin_context(ast_node n) {
		assert n.has_scope();
		n.get_symtab_var().set_parent(curr_sym);
		n.get_symtab_field().set_parent(curr_field);
		n.get_symtab_proc().set_parent(curr_proc);

		var_syms.addLast(curr_sym);
		field_syms.addLast(curr_field);
		proc_syms.addLast(curr_proc);

		curr_sym = n.get_symtab_var();
		curr_field = n.get_symtab_field();
		curr_proc = n.get_symtab_proc();

		// printf("push\n");
	}

	@Override
	public void end_context(ast_node n) {
		assert n.has_scope();
		curr_sym = var_syms.getLast();
		curr_field = field_syms.getLast();
		curr_proc = proc_syms.getLast();

		var_syms.removeLast();
		field_syms.removeLast();
		proc_syms.removeLast();
		// printf("pop\n");
	}

	public final void set_okay(boolean b) {
		_is_okay = _is_okay && b;
	}

	public final boolean is_okay() {
		return _is_okay;
	}

	public final boolean find_symbol_field(ast_field f) {
		ast_id driver = f.get_first();
		ast_id field = f.get_second();

		boolean is_okay = true;
		is_okay = GlobalMembersGm_new_typecheck_step1.gm_find_and_connect_symbol(driver, curr_sym) && is_okay;
		is_okay = GlobalMembersGm_new_typecheck_step1.gm_find_and_connect_symbol(field, curr_field) && is_okay;

		if (is_okay) {

			ast_typedecl name_type = driver.getTypeInfo();
			ast_typedecl field_type = field.getTypeInfo();
			assert name_type != null;
			assert field_type != null;

			// check the type of driver
			if (!(name_type.is_graph() || name_type.is_collection() || name_type.is_nodeedge() || name_type.is_node_edge_iterator() || name_type
					.is_collection_iterator())) // for group assignment - for
												// group assignment
			{
				GlobalMembersGm_error.gm_type_error(GM_ERROR_NONGRAPH_FIELD, driver);
				is_okay = false;
			}

			if (!field_type.is_property()) {
				GlobalMembersGm_error.gm_type_error(GM_ERROR_WRONG_PROPERTY, field, "property");
				is_okay = false;
			}

			if (!is_okay)
				return false;

			// n.X ==> n is node iterator, X is node prop
			// Edge(n).Y ==> n is nbr iterator, Y is edge prop. Edge(n) is the
			// current edge that goes to n

			if (f.is_rarrow()) {
				GMTYPE_T type = name_type.getTypeSummary();
				if (!(type.is_inout_nbr_node_iter_type() || (type == GMTYPE_T.GMTYPE_NODEITER_BFS))) {
					// not BFS, not in-out
					GlobalMembersGm_error.gm_type_error(GM_ERROR_INVALID_ITERATOR_FOR_RARROW, driver);
					return false;
				}
				if (!field_type.is_edge_property()) {
					GlobalMembersGm_error.gm_type_error(GM_ERROR_WRONG_PROPERTY, field, "Edge_Property");
					return false;
				}
			} else {

				if (name_type.is_graph() || name_type.is_collection()) {
					// to be resolved more later (group assignment)
				} else if (name_type.is_node_compatible()) {
					if (!field_type.is_node_property()) {
						GlobalMembersGm_error.gm_type_error(GM_ERROR_WRONG_PROPERTY, field, "Node_Property");
						return false;
					}
				} else if (name_type.is_edge_compatible()) {
					if (!field_type.is_edge_property()) {
						GlobalMembersGm_error.gm_type_error(GM_ERROR_WRONG_PROPERTY, field, "Edge_Property");
						return false;
					}
				} else {
					assert false;
				}
			}

			// check target graph matches
			if (!GlobalMembersGm_new_typecheck_step1.gm_check_target_graph(driver, field)) {
				GlobalMembersGm_error.gm_type_error(GM_ERROR_TARGET_MISMATCH, driver, field);
				return false;
			}
		}

		return is_okay;
	}

	public final boolean find_symbol_id(ast_id id) {
		return find_symbol_id(id, true);
	}

	// C++ TO JAVA CONVERTER NOTE: Java does not allow default values for
	// parameters. Overloaded methods are inserted above.
	// ORIGINAL LINE: boolean find_symbol_id(ast_id* id, boolean print_error =
	// true)
	public final boolean find_symbol_id(ast_id id, boolean print_error) {
		return GlobalMembersGm_new_typecheck_step1.gm_find_and_connect_symbol(id, curr_sym, print_error);
	}

	public final boolean find_symbol_field_id(ast_id id) {
		return GlobalMembersGm_new_typecheck_step1.gm_find_and_connect_symbol(id, curr_field);
	}

	// symbol checking for foreach and in-place reduction
	public final boolean gm_symbol_check_iter_header(ast_id it, ast_id src, GMTYPE_T iter_type) {
		return gm_symbol_check_iter_header(it, src, iter_type, null);
	}

	// C++ TO JAVA CONVERTER NOTE: Java does not allow default values for
	// parameters. Overloaded methods are inserted above.
	// ORIGINAL LINE: boolean gm_symbol_check_iter_header(ast_id* it, ast_id*
	// src, int iter_type, ast_id* src2 = null)
	public final boolean gm_symbol_check_iter_header(ast_id it, ast_id src, GMTYPE_T iter_type, ast_id src2) {
		boolean is_okay = true;
		// GRAPH
		if (iter_type.is_iteration_on_all_graph()) {
			is_okay = GlobalMembersGm_new_typecheck_step1.gm_check_target_is_defined(src, curr_sym, GlobalMembersGm_new_typecheck_step1.SHOULD_BE_A_GRAPH);
		}
		// items - collection
		else if (iter_type.is_iteration_on_collection()) {
			is_okay = GlobalMembersGm_new_typecheck_step1.gm_check_target_is_defined(src, curr_sym, GlobalMembersGm_new_typecheck_step1.SHOULD_BE_A_COLLECTION);
		}
		// items - property
		else if (iter_type.is_iteration_on_property()) {
			is_okay = GlobalMembersGm_new_typecheck_step1.gm_check_target_is_defined(src, curr_field, GlobalMembersGm_new_typecheck_step1.SHOULD_BE_A_PROPERTY);
		}
		// out.in.up.down
		else if (iter_type.is_iteration_on_neighbors_compatible()) {
			ast_id n = src; // f->get_source();
			is_okay = GlobalMembersGm_new_typecheck_step1.gm_find_and_connect_symbol(n, curr_sym); // source

			if (is_okay) {

				ast_typedecl type = n.getTypeInfo();
				if (!type.is_node_compatible()) {
					GlobalMembersGm_error.gm_type_error(GM_ERROR_NONNODE_TARGET, n, n);
					is_okay = false;
				}

				// In/Down is only available inside BFS -> checked at step 2
				if (iter_type.is_iteration_on_updown_levels()) {
					if (n.getTypeSummary().is_iteration_bfs()) {
						GlobalMembersGm_error.gm_type_error(GM_ERROR_NEED_BFS_ITERATION, n);
						is_okay = false;
					}
				}

				if (is_okay && iter_type.is_common_nbr_iter_type()) {
					assert src2 != null;
					is_okay = GlobalMembersGm_new_typecheck_step1.gm_find_and_connect_symbol(src2, curr_sym); // source

					if (is_okay) {
						// check if two sources have the same graph
						gm_symtab_entry e1 = src.getTypeInfo().get_target_graph_sym();
						gm_symtab_entry e2 = src2.getTypeInfo().get_target_graph_sym();
						assert e1 != null;
						if (e1 != e2) {
							GlobalMembersGm_error.gm_type_error(GM_ERROR_TARGET_MISMATCH, src2.get_line(), src2.get_col());
							is_okay = false;
						}
					}

				}
			}
		} else {
			System.out.println(iter_type.get_type_string());
			assert false;
		}

		if (!is_okay)
			return false;

		// --------------------------------------
		// create iterator
		// --------------------------------------
		ast_typedecl type;
		if (iter_type.is_iteration_on_collection()) {
			type = ast_typedecl.new_set_iterator(src.copy(true), iter_type);
		} else if (iter_type.is_iteration_on_property()) {
			type = ast_typedecl.new_property_iterator(src.copy(true), iter_type);
		} else if (iter_type.is_iteration_on_neighbors_compatible()) {
			type = ast_typedecl.new_nbr_iterator(src.copy(true), iter_type);
		} else {
			type = ast_typedecl.new_nodeedge_iterator(src.copy(true), iter_type);
		}

		if (iter_type.is_iteration_on_property())
			is_okay = GlobalMembersGm_new_typecheck_step1.gm_declare_symbol(curr_sym, it, type, GM_READ_AVAILABLE, GM_WRITE_NOT_AVAILABLE, curr_field);
		else if (src.getTypeInfo().is_collection_of_collection())
			is_okay = GlobalMembersGm_new_typecheck_step1.gm_declare_symbol(curr_sym, it, type, GM_READ_AVAILABLE, GM_WRITE_NOT_AVAILABLE, null,
					src.getTargetTypeSummary());
		else
			is_okay = GlobalMembersGm_new_typecheck_step1.gm_declare_symbol(curr_sym, it, type, GM_READ_AVAILABLE, GM_WRITE_NOT_AVAILABLE);

		if (type != null)
			type.dispose();

		return is_okay;
	}

	// symbol checking for foreach and in-place reduction
	public final boolean gm_symbol_check_bfs_header(ast_id it, ast_id src, ast_id root, GMTYPE_T iter_type) {
		// check source: should be a graph
		boolean is_okay = true;
		is_okay = GlobalMembersGm_new_typecheck_step1.gm_check_target_is_defined(src, curr_sym, GlobalMembersGm_new_typecheck_step1.SHOULD_BE_A_GRAPH);
		// check root:
		is_okay = GlobalMembersGm_new_typecheck_step1.gm_find_and_connect_symbol(root, curr_sym) && is_okay;
		if (is_okay) {
			// root should be a node. and target should be the graph
			ast_typedecl t_root = root.getTypeInfo();
			if (!t_root.is_node_compatible()) {
				GlobalMembersGm_error.gm_type_error(GM_ERROR_NONNODE_TARGET, root, root);
				is_okay = false;
			}
		}

		if (is_okay) {
			// check root is a node of src
			is_okay = GlobalMembersGm_new_typecheck_step1.gm_check_target_graph(src, root);
			if (!is_okay)
				GlobalMembersGm_error.gm_type_error(GM_ERROR_TARGET_MISMATCH, src, root);
		}

		// -----------------------------------------
		// create iteator
		// -----------------------------------------
		ast_typedecl type = ast_typedecl.new_nodeedge_iterator(src.copy(true), iter_type);
		is_okay = GlobalMembersGm_new_typecheck_step1.gm_declare_symbol(curr_sym, it, type, GM_READ_AVAILABLE, GM_WRITE_NOT_AVAILABLE) && is_okay;
		if (type != null)
			type.dispose();

		return is_okay;
	}

	// if sourceId is defined as a field variable (= is a property) the iter
	// type should be a property iterator
	private GMTYPE_T adjust_iter_type(ast_foreach fe) {
		if (curr_field.find_symbol(fe.get_source()) != null) {
			ast_id source = fe.get_source();
			gm_symtab_entry tabEntry = curr_field.find_symbol(source);
			GMTYPE_T targetType = tabEntry.getType().getTargetTypeSummary();
			GMTYPE_T newIterType = mapTargetToIterType(targetType);
			fe.set_iter_type(newIterType);
			return newIterType;
		} else {
			return fe.get_iter_type();
		}
	}

	private static GMTYPE_T mapTargetToIterType(GMTYPE_T targetType) {
		switch (targetType) {
		case GMTYPE_NSET:
		case GMTYPE_ESET:
			return GMTYPE_T.GMTYPE_PROPERTYITER_SET;
		case GMTYPE_NSEQ:
		case GMTYPE_ESEQ:
			return GMTYPE_T.GMTYPE_PROPERTYITER_SEQ;
		case GMTYPE_NORDER:
		case GMTYPE_EORDER:
			return GMTYPE_T.GMTYPE_PROPERTYITER_ORDER;
		default:
			assert false;
			return GMTYPE_T.GMTYPE_INVALID;
		}
	}
}
