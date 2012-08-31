package frontend;

import inc.GM_REDUCE_T;
import inc.gm_assignment_t;
import ast.AST_NODE_TYPE;
import ast.ast_assign;
import ast.ast_expr;
import ast.ast_id;
import ast.ast_idlist;
import ast.ast_sent;
import ast.ast_vardecl;

import common.gm_transform_helper;
import common.gm_apply;

public class gm_ss1_initial_expr extends gm_apply
{
	@Override
	public boolean apply(ast_sent s)
	{
		if (s.get_nodetype() != AST_NODE_TYPE.AST_VARDECL)
			return true;

		// if it has an initializer, create new sentence
		ast_vardecl v = (ast_vardecl) s;
		ast_expr e = v.get_init();

		if (e == null)
			return true;
		v.set_init(null);

		// should be single variable definition
		// should be non-field type.
		ast_idlist idl = v.get_idlist();
		assert idl.get_length() == 1;
		ast_id id = idl.get_item(0).copy();

		// new assign statement
		ast_assign a = ast_assign.new_assign_scala(id, e, gm_assignment_t.GMASSIGN_NORMAL, null, GM_REDUCE_T.GMREDUCE_NULL);

		// add this sententence next to current statement
		gm_transform_helper.gm_add_sent_after(v, a, false); // no fix symtab

		return true;
	}
}
//bool gm_frontend::do_syntax_sugar_1(ast_procdef* p)
