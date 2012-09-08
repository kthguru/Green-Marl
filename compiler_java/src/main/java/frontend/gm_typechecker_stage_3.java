package frontend;

import static common.GM_ERRORS_AND_WARNINGS.GM_ERROR_INVALID_BUILTIN_ARG_TYPE;
import static common.GM_ERRORS_AND_WARNINGS.GM_ERROR_NEED_BOOLEAN;
import static common.GM_ERRORS_AND_WARNINGS.GM_ERROR_OPERATOR_MISMATCH;
import static common.GM_ERRORS_AND_WARNINGS.GM_ERROR_OPERATOR_MISMATCH2;
import static common.GM_ERRORS_AND_WARNINGS.GM_ERROR_TARGET_MISMATCH;
import static common.GM_ERRORS_AND_WARNINGS.GM_ERROR_TYPE_CONVERSION;
import static common.GM_ERRORS_AND_WARNINGS.GM_ERROR_TYPE_CONVERSION_BOOL_NUM;
import inc.GMTYPE_T;
import inc.GM_OPS_T;
import inc.GM_REDUCE_T;

import java.util.LinkedList;

import tangible.RefObject;
import ast.ast_expr;
import ast.ast_expr_builtin;
import ast.ast_expr_reduce;
import ast.ast_id;
import ast.ast_typedecl;

import common.gm_error;
import common.gm_apply;
import common.gm_builtin_def;
import common.gm_method_id_t;

/**
* Type-check Step 3: 
*   (1) Resolve type of each expression
*   (2) Check function call arguments
*   (3) Check argminmax assign count
*----------------------------------------------------------------
* resolve type of every sub-expression
*/
public class gm_typechecker_stage_3 extends gm_apply {
	
	/** expression, dest-type */
	public java.util.HashMap<ast_expr, GMTYPE_T> coercion_targets = new java.util.HashMap<ast_expr, GMTYPE_T>();

	public gm_typechecker_stage_3() {
		_is_okay = true;
		set_for_expr(true);
	}

	// post apply
	public final boolean apply(ast_expr e) {
		boolean okay = true;
		switch (e.get_opclass()) {
		case GMEXPR_ID:
			e.set_type_summary(e.get_id().getTypeSummary());
			// for comparison
			{
				ast_typedecl t = e.get_id().getTypeInfo();
				if (t.is_node_edge_compatible() || t.is_collection()) {
					gm_symtab_entry g = t.get_target_graph_sym();
					assert g != null;
					e.set_bound_graph(g);
				}
			}
			break;

		case GMEXPR_FIELD:
			e.set_type_summary(e.get_field().get_second().getTargetTypeSummary());
			{
				ast_typedecl t = e.get_field().getTargetTypeInfo();
				if (t.is_node_edge_compatible() || t.is_collection()) {
					gm_symtab_entry g = t.get_target_graph_sym();
					assert g != null;
					e.set_bound_graph(g);
				}
			}
			break;

		case GMEXPR_IVAL:
		case GMEXPR_FVAL:
		case GMEXPR_BVAL:
			// already done
			break;

		case GMEXPR_INF:
		case GMEXPR_NIL:
			break; // will be resovled later

		case GMEXPR_LUOP:
		case GMEXPR_UOP:
			okay = check_uop(e);
			break;
		case GMEXPR_LBIOP:
		case GMEXPR_BIOP:
		case GMEXPR_COMP:
			okay = check_binary(e);
			break;

		case GMEXPR_REDUCE: {
			ast_expr_reduce r = (ast_expr_reduce) e;
			GMTYPE_T b_type = r.get_body().get_type_summary();
			GM_REDUCE_T r_type = r.get_reduce_type();
			if (b_type.is_unknown_type()) {
				okay = false;
			} else {
				// body type <-> reduce op type: done at typecheck step 5.
				if (r_type == GM_REDUCE_T.GMREDUCE_AVG) {
					if (b_type == GMTYPE_T.GMTYPE_FLOAT)
						r.set_type_summary(GMTYPE_T.GMTYPE_FLOAT);
					else
						r.set_type_summary(GMTYPE_T.GMTYPE_DOUBLE);
				} else
					r.set_type_summary(b_type);
			}
		}
			break;
		case GMEXPR_TER:
			okay = check_ter(e);
			break;

		case GMEXPR_BUILTIN:
		case GMEXPR_BUILTIN_FIELD: {
			ast_expr_builtin b = (ast_expr_builtin) e;
			okay = check_builtin(b);
		}
			break;
		case GMEXPR_FOREIGN:
			e.set_type_summary(GMTYPE_T.GMTYPE_FOREIGN_EXPR);
			okay = true;
			break;
		default:
			assert false;
			break;
		}

		if (okay) {
			assert !e.get_type_summary().is_unknown_type();
		}
		set_okay(okay);
		return okay;
	}

	public final void set_okay(boolean b) {
		_is_okay = b && _is_okay;
	}

	public final boolean is_okay() {
		return _is_okay;
	}

	private boolean _is_okay;

	// type resolve for u-op
	private boolean check_uop(ast_expr e) {
		GM_OPS_T op_type = e.get_optype();
		GMTYPE_T exp_type = e.get_left_op().get_type_summary();
		int l = e.get_line();
		int c = e.get_col();
		if (exp_type.is_unknown_type()) {
			return false; // no need to check
		}
		if (op_type == GM_OPS_T.GMOP_TYPECONVERSION) {
			// should be alredy dest_type;
			GMTYPE_T dest_type = e.get_type_summary();
			if (!dest_type.is_prim_type()) { // destination type
				gm_error.gm_type_error(GM_ERROR_TYPE_CONVERSION, l, c);
				return false;
			}

			if (!exp_type.is_prim_type() && !exp_type.is_nodeedge_type()) // source
																			// type
			{
				gm_error.gm_type_error(GM_ERROR_TYPE_CONVERSION, l, c);
				return false;
			}

			//
			boolean possible = (dest_type.is_numeric_type() && exp_type.is_numeric_type()) || (dest_type.is_boolean_type() && exp_type.is_boolean_type())
					|| (dest_type.is_numeric_type() && exp_type.is_nodeedge_type()) || false;

			if (!possible) {
				gm_error.gm_type_error(GM_ERROR_TYPE_CONVERSION_BOOL_NUM, l, c, "");
				return false;
			}

			return true;
		} // not
		else if (op_type.is_boolean_op()) {
			if (!exp_type.is_boolean_type()) {
				gm_error.gm_type_error(GM_ERROR_OPERATOR_MISMATCH, l, c, op_type.get_op_string(), exp_type.get_type_string());
				return false;
			}

			e.set_type_summary(exp_type);
			return true;
		} // neg or abs
		else if (op_type.is_numeric_op()) {
			if (!exp_type.is_numeric_type()) {
				gm_error.gm_type_error(GM_ERROR_OPERATOR_MISMATCH, l, c, op_type.get_op_string(), exp_type.get_type_string());
				return false;
			}

			e.set_type_summary(exp_type);
			return true;
		}

		assert false;

		return false;
	}

	// comparison (eq, neq and less)
	private boolean check_binary(ast_expr e) {
		GM_OPS_T op_type = e.get_optype();
		GMTYPE_T l_type = e.get_left_op().get_type_summary();
		GMTYPE_T r_type = e.get_right_op().get_type_summary();
		int l = e.get_line();
		int c = e.get_col();

		// result is always BOOL
		if (op_type.is_boolean_op() || op_type.is_eq_or_less_op())
			e.set_type_summary(GMTYPE_T.GMTYPE_BOOL);

		if (l_type.is_unknown_type() || r_type.is_unknown_type()) {
			return false; // no need to check any further
		}

		// special case inside group assignment
		// e.g> G.x = (G == n) ? 1 : 0;
		if (op_type == GM_OPS_T.GMOP_EQ) {
			GMTYPE_T alt_type_l = e.get_left_op().get_alternative_type();
			if (alt_type_l != GMTYPE_T.GMTYPE_UNKNOWN) {
				assert e.get_left_op().is_id();
				if (check_special_case_inside_group_assign(e.get_left_op().get_id(), alt_type_l, e.get_right_op())) {
					e.get_left_op().set_type_summary(alt_type_l);
					return true;
				}
			}
			GMTYPE_T alt_type_r = e.get_left_op().get_alternative_type();
			if (alt_type_r != GMTYPE_T.GMTYPE_UNKNOWN) {
				assert e.get_right_op().is_id();
				if (check_special_case_inside_group_assign(e.get_right_op().get_id(), alt_type_r, e.get_left_op())) {
					e.get_right_op().set_type_summary(alt_type_l);
					return true;
				}
			}
		}

		RefObject<GMTYPE_T> tempRef_result_type = new RefObject<GMTYPE_T>(null);
		RefObject<GMTYPE_T> tempRef_l_new = new RefObject<GMTYPE_T>(null);
		RefObject<GMTYPE_T> tempRef_r_new = new RefObject<GMTYPE_T>(null);
		RefObject<Boolean> tempRef_w1_warn = new RefObject<Boolean>(null);
		RefObject<Boolean> tempRef_w2_warn = new RefObject<Boolean>(null);
		boolean okay = Oprules.gm_is_compatible_type(op_type, l_type, r_type, tempRef_result_type, tempRef_l_new, tempRef_r_new,
				tempRef_w1_warn, tempRef_w2_warn);

		GMTYPE_T result_type = tempRef_result_type.argvalue;
		GMTYPE_T l_new = tempRef_l_new.argvalue;
		GMTYPE_T r_new = tempRef_r_new.argvalue;
		boolean w1_warn = tempRef_w1_warn.argvalue;
		boolean w2_warn = tempRef_w2_warn.argvalue;

		if (!okay) {
			gm_error.gm_type_error(GM_ERROR_OPERATOR_MISMATCH2, l, c, op_type.get_op_string(), l_type.get_type_string(),
					r_type.get_type_string());

			return false;
		}

		// node/edge
		if (l_type.has_target_graph_type()) {
			gm_symtab_entry l_sym = e.get_left_op().get_bound_graph();
			gm_symtab_entry r_sym = e.get_right_op().get_bound_graph();

			if (l_sym == null) {
				// printf("TYPE = %s\n" gm_get_type_string(
				// (e->get_left_op()->get_type_summary()));
				assert e.get_left_op().get_type_summary().is_nil_type();
			}

			if (r_sym == null) {
				assert e.get_right_op().get_type_summary().is_nil_type();
			}

			if ((l_sym != null) && (r_sym != null) && (l_sym != r_sym)) {
				gm_error.gm_type_error(GM_ERROR_TARGET_MISMATCH, l, c);
				return false;
			}

			e.set_bound_graph(l_sym);
		}

		e.set_type_summary(result_type);

		if (w1_warn && l_type.is_prim_type()) {
			// adding explicit coercions
			if (!e.get_left_op().is_literal()) {
				System.out.printf("warning: adding type conversion %s->%s\n", l_type.get_type_string(), l_new.get_type_string());
				coercion_targets.put(e.get_left_op(), l_new);
			}
		}
		if (w2_warn && r_type.is_prim_type()) {
			// adding explicit coercions
			if (!e.get_right_op().is_literal()) {
				System.out.printf("warning: adding type conversion %s->%s\n", r_type.get_type_string(), r_new.get_type_string());
				coercion_targets.put(e.get_right_op(), r_new);
			}
		}

		return true;
	}

	private boolean check_ter(ast_expr e) {
		GMTYPE_T l_type = e.get_left_op().get_type_summary();
		GMTYPE_T r_type = e.get_right_op().get_type_summary();
		GMTYPE_T c_type = e.get_cond_op().get_type_summary();
		int l = e.get_line();
		int c = e.get_col();

		if (l_type.is_unknown_type() || r_type.is_unknown_type() || c_type.is_unknown_type()) {
			return false; // no need to check
		}

		if (!c_type.is_boolean_type()) {
			gm_error.gm_type_error(GM_ERROR_NEED_BOOLEAN, l, c);
			return false;
		}

		// now check the binary part of the expression
		return check_binary(e);
	}

	private boolean check_builtin(ast_expr_builtin b) {

		boolean okay = check_arguments(b);
		gm_builtin_def def = b.get_builtin_def();
		GMTYPE_T fun_ret_type = def.get_result_type_summary();
		b.set_type_summary(fun_ret_type);

		if (fun_ret_type.has_target_graph_type()) {
			if (b.get_driver().getTypeInfo().is_graph()) {
				b.set_bound_graph(b.get_driver().getSymInfo());
			} else
				b.set_bound_graph(b.get_driver().getTypeInfo().get_target_graph_sym());
			// assert(false); // to be done
		}
		// assert(!gm_has_target_graph_type(fun_ret_type));
		return okay;
	}

	private boolean check_arguments(ast_expr_builtin b) {

		boolean okay = true;

		LinkedList<ast_expr> args = b.get_args();
		gm_builtin_def def = b.get_builtin_def();
		int position = 0;
		for (ast_expr e : args) {
			GMTYPE_T currentType = e.get_type_summary();
			GMTYPE_T def_type = def.get_arg_type(position);
			if (currentType.is_unknown_type()) {
				okay = false;
				position++;
				continue;
			}
			boolean isCompatible;
			RefObject<Boolean> warning_ref = new RefObject<Boolean>(null);
			RefObject<GMTYPE_T> coerced_type_ref = new RefObject<GMTYPE_T>(null);
			if (b.get_source_type().is_collection_of_collection_type())
				isCompatible = is_compatible_type_collection_of_collection(b.get_driver().getTargetTypeSummary(),
						currentType, def.get_method_id());
			else
				isCompatible = gm_typecheck.gm_is_compatible_type_for_assign(def_type, currentType, coerced_type_ref, warning_ref);

			boolean warning = warning_ref.argvalue;

			if (!isCompatible) {
				String temp = String.format("%d", position + 1);
				gm_error.gm_type_error(GM_ERROR_INVALID_BUILTIN_ARG_TYPE, b.get_line(), b.get_col(), b.get_callname(), temp);
				okay = false;
			}
			if (warning) {
				// [XXX] to be coerced
				// assert(false);
			}
			position++;
		}
		return okay;
	}
	
	private static boolean check_special_case_inside_group_assign(ast_id l_id, GMTYPE_T alt_type_l, ast_expr r) {

		GMTYPE_T r_type = r.get_type_summary();

		if (alt_type_l.is_node_compatible_type() && !r_type.is_node_compatible_type())
			return false;		
		if (alt_type_l.is_edge_compatible_type() && !r_type.is_edge_compatible_type())
			return false;

		assert l_id.getTypeInfo().is_graph() || l_id.getTypeInfo().is_collection();

		if (l_id.getTypeInfo().is_graph() && (l_id.getSymInfo() != r.get_bound_graph()))
			return false;
		if (l_id.getTypeInfo().is_collection() && (l_id.getTypeInfo().get_target_graph_sym() != r.get_bound_graph()))
			return false;

		return true;
	}
	
	private static boolean is_compatible_type_collection_of_collection(GMTYPE_T gmtype_T, GMTYPE_T currentType, gm_method_id_t methodId) {
		// TODO find better way to do this
		switch (methodId) {
		case GM_BLTIN_SET_ADD:
		case GM_BLTIN_SET_ADD_BACK:
			return gmtype_T == currentType;
		case GM_BLTIN_SET_REMOVE:
		case GM_BLTIN_SET_REMOVE_BACK:
		case GM_BLTIN_SET_SIZE:
			return true;
		default:
			assert false;
			return false;
		}
	}
}