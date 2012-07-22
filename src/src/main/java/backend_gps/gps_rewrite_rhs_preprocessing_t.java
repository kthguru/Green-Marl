package backend_gps;

import ast.AST_NODE_TYPE;
import ast.ast_foreach;
import ast.ast_sent;
import inc.GlobalMembersGm_backend_gps;

import common.GlobalMembersGm_transform_helper;
import common.gm_apply;

//C++ TO JAVA CONVERTER NOTE: The following #define macro was replaced in-line:
///#define TO_STR(X) #X
//C++ TO JAVA CONVERTER NOTE: The following #define macro was replaced in-line:
///#define DEF_STRING(X) static const char *X = "X"
//C++ TO JAVA CONVERTER NOTE: The following #define macro was replaced in-line:
///#define GM_COMPILE_STEP(CLASS, DESC) class CLASS : public gm_compile_step { private: CLASS() {set_description(DESC);}public: virtual void process(ast_procdef*p); virtual gm_compile_step* get_instance(){return new CLASS();} static gm_compile_step* get_factory(){return new CLASS();} };
//C++ TO JAVA CONVERTER NOTE: The following #define macro was replaced in-line:
///#define GM_COMPILE_STEP_FACTORY(CLASS) CLASS::get_factory()

//-----------------------------------------------------------------
// Rewrite expressions to make the messages compact
//-----------------------------------------------------------------
//
// Foreach (n: G.Nodes) {
//     Foreach(t: n.Nbrs) {
//         if (t.A + n.A > 10) {
//            t.B += n.D * n.E + t.C;
//         }
//     }
// }
// ==>
// Foreach (n: G.Nodes) {
//     Foreach(t: n.Nbrs) {
//         <type> _t1 = n.A;
//         <type> _t2 = n.D * n.E;
//         if (t.A + _t1 > 10) {
//            t.B += _t2 + t.C;
//         }
//     }
// }
//-----------------------------------------------------------------

public class gps_rewrite_rhs_preprocessing_t extends gm_apply
{
	public gps_rewrite_rhs_preprocessing_t()
	{
		set_for_sent(true);
	}
	public final boolean apply(ast_sent s)
	{
		if (s.get_nodetype() == AST_NODE_TYPE.AST_FOREACH)
		{
			if (s.find_info_bool(GlobalMembersGm_backend_gps.GPS_FLAG_IS_INNER_LOOP))
			{
				ast_foreach fe = (ast_foreach) s;
				if (fe.get_body().get_nodetype() != AST_NODE_TYPE.AST_SENTBLOCK)
				{
					inner_loops.addLast(fe);
				}
			}
		}
		return true;
	}
	public final void process()
	{
		java.util.Iterator<ast_foreach> I;
		for (I = inner_loops.iterator(); I.hasNext();)
		{
			ast_foreach fe = I.next();
			ast_sent s = fe.get_body();
			GlobalMembersGm_transform_helper.gm_make_it_belong_to_sentblock(s);

			assert s.get_parent().get_nodetype() == AST_NODE_TYPE.AST_SENTBLOCK;
			s.get_parent().add_info_int(GlobalMembersGm_backend_gps.GPS_INT_SYNTAX_CONTEXT, gm_gps_new_scope_analysis_t.GPS_NEW_SCOPE_IN);

			assert fe.get_body().get_nodetype() == AST_NODE_TYPE.AST_SENTBLOCK;
			//printf("(1)fe = %p, sb = %p\n", fe, fe->get_body());
		}
	}

	private java.util.LinkedList<ast_foreach> inner_loops = new java.util.LinkedList<ast_foreach>();
}