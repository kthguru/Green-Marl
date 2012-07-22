package frontend;

import inc.GMTYPE_T;
import inc.GM_OPS_T;
import inc.GM_REDUCE_T;
import inc.GlobalMembersGm_defs;
import ast.ast_expr;
import ast.ast_expr_builtin;
import ast.ast_expr_reduce;
import ast.ast_typedecl;

import common.GM_ERRORS_AND_WARNINGS;
import common.GlobalMembersGm_error;
import common.GlobalMembersGm_misc;
import common.gm_apply;
import common.gm_builtin_def;

//C++ TO JAVA CONVERTER NOTE: The following #define macro was replaced in-line:
///#define TO_STR(X) #X
//C++ TO JAVA CONVERTER NOTE: The following #define macro was replaced in-line:
///#define DEF_STRING(X) static const char *X = "X"
//C++ TO JAVA CONVERTER NOTE: The following #define macro was replaced in-line:
///#define GM_COMPILE_STEP(CLASS, DESC) class CLASS : public gm_compile_step { private: CLASS() {set_description(DESC);}public: virtual void process(ast_procdef*p); virtual gm_compile_step* get_instance(){return new CLASS();} static gm_compile_step* get_factory(){return new CLASS();} };
//C++ TO JAVA CONVERTER NOTE: The following #define macro was replaced in-line:
///#define GM_COMPILE_STEP_FACTORY(CLASS) CLASS::get_factory()
//C++ TO JAVA CONVERTER NOTE: The following #define macro was replaced in-line:
///#define AUX_INFO(X,Y) "X"":""Y"
///#define GM_BLTIN_MUTATE_GROW 1
///#define GM_BLTIN_MUTATE_SHRINK 2
//C++ TO JAVA CONVERTER NOTE: The following #define macro was replaced in-line:
///#define GM_BLTIN_FLAG_TRUE true

//----------------------------------------------------------------
// Type-check Step 3: 
//   (1) Resolve type of each expression
//   (2) Check function call arguments
//   (3) Check argminmax assign count
//----------------------------------------------------------------

// resolve type of every sub-expression
public class gm_typechecker_stage_3 extends gm_apply
{
	public gm_typechecker_stage_3()
	{
		_is_okay = true;
		set_for_expr(true);
	}

	// post apply
	public final boolean apply(ast_expr e)
	{
		boolean okay = true;
		switch (e.get_opclass())
		{
			case GMEXPR_ID:
				e.set_type_summary(e.get_id().getTypeSummary());
				// for comparison 
				{
					ast_typedecl t = e.get_id().getTypeInfo();
					if (t.is_node_edge_compatible() || t.is_collection())
					{
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
					if (t.is_node_edge_compatible() || t.is_collection())
					{
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

			case GMEXPR_REDUCE:
			{
				ast_expr_reduce r = (ast_expr_reduce) e;
				int b_type = r.get_body().get_type_summary();
				int r_type = r.get_reduce_type();
				if (GlobalMembersGm_defs.gm_is_unknown_type(b_type))
				{
					okay = false;
				}
				else
				{
					// body type <-> reduce op type: done at typecheck step 5.
					if (r_type == GM_REDUCE_T.GMREDUCE_AVG.getValue())
					{
						if (b_type == GMTYPE_T.GMTYPE_FLOAT.getValue())
							r.set_type_summary(GMTYPE_T.GMTYPE_FLOAT);
						else
							r.set_type_summary(GMTYPE_T.GMTYPE_DOUBLE);
					}
					else
						r.set_type_summary(b_type);
				}
			}
				break;
			case GMEXPR_TER:
				okay = check_ter(e);
				break;

			case GMEXPR_BUILTIN:
			case GMEXPR_BUILTIN_FIELD:
			{
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

		if (okay)
		{
			assert!GlobalMembersGm_defs.gm_is_unknown_type(e.get_type_summary());
		}
		set_okay(okay);
		return okay;
	}

	public final void set_okay(boolean b)
	{
		_is_okay = b && _is_okay;
	}
	public final boolean is_okay()
	{
		return _is_okay;
	}

	private boolean _is_okay;

	// type resolve for u-op
	private boolean check_uop(ast_expr e)
	{
		int op_type = e.get_optype();
		int exp_type = e.get_left_op().get_type_summary();
		int l = e.get_line();
		int c = e.get_col();
		if (GlobalMembersGm_defs.gm_is_unknown_type(exp_type))
		{
			return false; // no need to check
		}
		if (op_type == GM_OPS_T.GMOP_TYPEC.getValue())
		{
			// should be alredy dest_type;
			int dest_type = e.get_type_summary();
			if (!GlobalMembersGm_defs.gm_is_prim_type(dest_type)) // destination type
			{
				GlobalMembersGm_error.gm_type_error(GM_ERRORS_AND_WARNINGS.GM_ERROR_TYPE_CONVERSION, l, c);
				return false;
			}

			if (!GlobalMembersGm_defs.gm_is_prim_type(exp_type) && !GlobalMembersGm_defs.gm_is_nodeedge_type(exp_type)) // source type
			{
				GlobalMembersGm_error.gm_type_error(GM_ERRORS_AND_WARNINGS.GM_ERROR_TYPE_CONVERSION, l, c);
				return false;
			}

			//
			boolean possible = (GlobalMembersGm_defs.gm_is_numeric_type(dest_type) && GlobalMembersGm_defs.gm_is_numeric_type(exp_type)) || (GlobalMembersGm_defs.gm_is_boolean_type(dest_type) && GlobalMembersGm_defs.gm_is_boolean_type(exp_type)) || (GlobalMembersGm_defs.gm_is_numeric_type(dest_type) && GlobalMembersGm_defs.gm_is_nodeedge_type(exp_type)) || false;

			if (!possible)
			{
				GlobalMembersGm_error.gm_type_error(GM_ERRORS_AND_WARNINGS.GM_ERROR_TYPE_CONVERSION_BOOL_NUM, l, c, "");
				return false;
			}

			return true;
		} // not
		else if (GlobalMembersGm_defs.gm_is_boolean_op(op_type))
		{
			if (!GlobalMembersGm_defs.gm_is_boolean_type(exp_type))
			{
				GlobalMembersGm_error.gm_type_error(GM_ERRORS_AND_WARNINGS.GM_ERROR_OPERATOR_MISMATCH, l, c, GlobalMembersGm_misc.gm_get_op_string(op_type), GlobalMembersGm_misc.gm_get_type_string(exp_type));
				return false;
			}

			e.set_type_summary(exp_type);
			return true;
		} // neg or abs
		else if (GlobalMembersGm_defs.gm_is_numeric_op(op_type))
		{
			if (!GlobalMembersGm_defs.gm_is_numeric_type(exp_type))
			{
				GlobalMembersGm_error.gm_type_error(GM_ERRORS_AND_WARNINGS.GM_ERROR_OPERATOR_MISMATCH, l, c, GlobalMembersGm_misc.gm_get_op_string(op_type), GlobalMembersGm_misc.gm_get_type_string(exp_type));
				return false;
			}

			e.set_type_summary(exp_type);
			return true;
		}

		assert false;

		return false;
	}

	// comparison (eq, neq and less)
	private boolean check_binary(ast_expr e)
	{
		int op_type = e.get_optype();
		int l_type = e.get_left_op().get_type_summary();
		int r_type = e.get_right_op().get_type_summary();
		int l = e.get_line();
		int c = e.get_col();

		// result is always BOOL
		if (GlobalMembersGm_defs.gm_is_boolean_op(op_type) || GlobalMembersGm_defs.gm_is_eq_or_less_op(op_type))
			e.set_type_summary(GMTYPE_T.GMTYPE_BOOL);

		if (GlobalMembersGm_defs.gm_is_unknown_type(l_type) || GlobalMembersGm_defs.gm_is_unknown_type(r_type))
		{
			return false; // no need to check any further
		}

		// special case inside group assignment
		// e.g> G.x = (G == n) ? 1 : 0;
		if (op_type == GM_OPS_T.GMOP_EQ.getValue())
		{
			int alt_type_l = e.get_left_op().get_alternative_type();
			if (alt_type_l != GMTYPE_T.GMTYPE_UNKNOWN.getValue())
			{
				assert e.get_left_op().is_id();
				if (GlobalMembersGm_new_typecheck_step3.check_special_case_inside_group_assign(e.get_left_op().get_id(), alt_type_l, e.get_right_op()))
				{
					e.get_left_op().set_type_summary(alt_type_l);
					return true;
				}
			}
			int alt_type_r = e.get_left_op().get_alternative_type();
			if (alt_type_r != GMTYPE_T.GMTYPE_UNKNOWN.getValue())
			{
				assert e.get_right_op().is_id();
				if (GlobalMembersGm_new_typecheck_step3.check_special_case_inside_group_assign(e.get_right_op().get_id(), alt_type_r, e.get_left_op()))
				{
					e.get_right_op().set_type_summary(alt_type_l);
					return true;
				}
			}
		}

		int result_type;
		int l_new;
		int r_new;
		boolean w1_warn;
		boolean w2_warn;

		tangible.RefObject<Integer> tempRef_result_type = new tangible.RefObject<Integer>(result_type);
		tangible.RefObject<Integer> tempRef_l_new = new tangible.RefObject<Integer>(l_new);
		tangible.RefObject<Integer> tempRef_r_new = new tangible.RefObject<Integer>(r_new);
		tangible.RefObject<Boolean> tempRef_w1_warn = new tangible.RefObject<Boolean>(w1_warn);
		tangible.RefObject<Boolean> tempRef_w2_warn = new tangible.RefObject<Boolean>(w2_warn);
		boolean okay = GlobalMembersGm_typecheck_oprules.gm_is_compatible_type(op_type, l_type, r_type, tempRef_result_type, tempRef_l_new, tempRef_r_new, tempRef_w1_warn, tempRef_w2_warn);
		result_type = tempRef_result_type.argvalue;
		l_new = tempRef_l_new.argvalue;
		r_new = tempRef_r_new.argvalue;
		w1_warn = tempRef_w1_warn.argvalue;
		w2_warn = tempRef_w2_warn.argvalue;

		if (!okay)
		{
			GlobalMembersGm_error.gm_type_error(GM_ERRORS_AND_WARNINGS.GM_ERROR_OPERATOR_MISMATCH2, l, c, GlobalMembersGm_misc.gm_get_op_string(op_type), GlobalMembersGm_misc.gm_get_type_string(l_type), GlobalMembersGm_misc.gm_get_type_string(r_type));

			return false;
		}

		// node/edge
		if (GlobalMembersGm_defs.gm_has_target_graph_type(l_type))
		{
			gm_symtab_entry l_sym = e.get_left_op().get_bound_graph();
			gm_symtab_entry r_sym = e.get_right_op().get_bound_graph();

			if (l_sym == null)
			{
				//printf("TYPE = %s\n" gm_get_type_string(
				//(e->get_left_op()->get_type_summary()));
				assert GlobalMembersGm_defs.gm_is_nil_type(e.get_left_op().get_type_summary());
			}

			if (r_sym == null)
			{
				assert GlobalMembersGm_defs.gm_is_nil_type(e.get_right_op().get_type_summary());
			}

			if ((l_sym != null) && (r_sym != null) && (l_sym != r_sym))
			{
				GlobalMembersGm_error.gm_type_error(GM_ERRORS_AND_WARNINGS.GM_ERROR_TARGET_MISMATCH, l, c);
				return false;
			}

			e.set_bound_graph(l_sym);
		}

		e.set_type_summary(result_type);

		if (w1_warn && GlobalMembersGm_defs.gm_is_prim_type(l_type))
		{
			// adding explicit coercions
			if (!e.get_left_op().is_literal())
			{
				System.out.printf("warning: adding type conversion %s->%s\n", GlobalMembersGm_misc.gm_get_type_string(l_type), GlobalMembersGm_misc.gm_get_type_string(l_new));
				coercion_targets.put(e.get_left_op(), l_new);
			}
		}
		if (w2_warn && GlobalMembersGm_defs.gm_is_prim_type(r_type))
		{
			// adding explicit coercions
			if (!e.get_right_op().is_literal())
			{
				System.out.printf("warning: adding type conversion %s->%s\n", GlobalMembersGm_misc.gm_get_type_string(r_type), GlobalMembersGm_misc.gm_get_type_string(r_new));
				coercion_targets.put(e.get_right_op(), r_new);
			}
		}

		return true;
	}
	private boolean check_ter(ast_expr e)
	{
		int op_type = e.get_optype();
		int l_type = e.get_left_op().get_type_summary();
		int r_type = e.get_right_op().get_type_summary();
		int c_type = e.get_cond_op().get_type_summary();
		int l = e.get_line();
		int c = e.get_col();

		if (GlobalMembersGm_defs.gm_is_unknown_type(l_type) || GlobalMembersGm_defs.gm_is_unknown_type(r_type) || GlobalMembersGm_defs.gm_is_unknown_type(c_type))
		{
			return false; // no need to check
		}

		if (!GlobalMembersGm_defs.gm_is_boolean_type(c_type))
		{
			GlobalMembersGm_error.gm_type_error(GM_ERRORS_AND_WARNINGS.GM_ERROR_NEED_BOOLEAN, l, c);
			return false;
		}

		// now check the binary part of the expression
		return check_binary(e);
	}
	private boolean check_builtin(ast_expr_builtin b)
	{

		boolean okay = check_arguments(b);
		gm_builtin_def def = b.get_builtin_def();
		int fun_ret_type = def.get_result_type_summary();
		b.set_type_summary(fun_ret_type);

		if (GlobalMembersGm_defs.gm_has_target_graph_type(fun_ret_type))
		{
			if (b.get_driver().getTypeInfo().is_graph())
			{
				b.set_bound_graph(b.get_driver().getSymInfo());
			}
			else
				b.set_bound_graph(b.get_driver().getTypeInfo().get_target_graph_sym());
			//assert(false); // to be done
		}
		//assert(!gm_has_target_graph_type(fun_ret_type));
		return okay;
	}
	private boolean check_arguments(ast_expr_builtin b)
	{

		boolean okay = true;

		java.util.LinkedList<ast_expr> args = b.get_args();
		java.util.Iterator<ast_expr> iter;
		gm_builtin_def def = b.get_builtin_def();
		int position = 0;
		for (iter = args.iterator(); iter.hasNext(); iter++, position++)
		{
			ast_expr e = iter.next();
			int currentType = e.get_type_summary();
			int def_type = def.get_arg_type(position);
			if (GlobalMembersGm_defs.gm_is_unknown_type(currentType))
			{
				okay = false;
				continue;
			}
			boolean warning;
			int coerced_type;
			boolean isCompatible;
			if (GlobalMembersGm_defs.gm_is_collection_of_collection_type(b.get_source_type()))
				isCompatible = GlobalMembersGm_new_typecheck_step3.gm_is_compatible_type_collection_of_collection(b.get_driver().getTargetTypeSummary(), currentType, def.get_method_id());
			else
				isCompatible = GlobalMembersGm_typecheck.gm_is_compatible_type_for_assign(def_type, currentType, coerced_type, warning);
			if (!isCompatible)
			{
				String temp = new String(new char[20]);
				String.format(temp, "%d", position + 1);
				GlobalMembersGm_error.gm_type_error(GM_ERRORS_AND_WARNINGS.GM_ERROR_INVALID_BUILTIN_ARG_TYPE, b.get_line(), b.get_col(), b.get_callname(), temp);
				okay = false;
			}
			if (warning)
			{
				// [XXX] to be coerced
				//assert(false);
			}
		}
		return okay;
	}

	// expression, dest-type
	public java.util.HashMap<ast_expr, Integer> coercion_targets = new java.util.HashMap<ast_expr, Integer>();
}