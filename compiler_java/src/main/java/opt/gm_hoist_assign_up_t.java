package opt;

import ast.AST_NODE_TYPE;
import ast.ast_assign;
import ast.ast_sent;

//--------------------------------------------------------------------
// hoist up initialization assignment as far as possible
//--------------------------------------------------------------------
public class gm_hoist_assign_up_t extends gm_hoist_normal_sent_t
{
	// need post apply.
	@Override
	protected boolean check_target(ast_sent target)
	{
		// check if assign
		if (target.get_nodetype() != AST_NODE_TYPE.AST_ASSIGN)
			return false;
		ast_assign a = (ast_assign) target;

		if (a.is_reduce_assign() || a.is_defer_assign())
			return false;

		// check if constant assign
		is_const_check.prepare();
		a.get_rhs().traverse_pre(is_const_check);
		return is_const_check.is_const();
	}

	@Override
	protected boolean check_trivial_pred(ast_sent S)
	{
		if ((S.get_nodetype() == AST_NODE_TYPE.AST_VARDECL) || (S.get_nodetype() == AST_NODE_TYPE.AST_ASSIGN))
			return true;
		else
			return false;
	}

	protected gm_check_if_constant_t is_const_check = new gm_check_if_constant_t();
}
//bool gm_independent_optimize::do_hoist_assign(ast_procdef* p)